package com.oficina.tech_challenge.infrastructure.messaging;

import com.oficina.tech_challenge.application.interfaces.BillingQuoteDispatch;
import com.oficina.tech_challenge.application.interfaces.BillingCheckoutQuery;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name="oficina.billing-quote.enabled", havingValue="true")
class BillingQuoteDispatcher implements BillingQuoteDispatch, BillingCheckoutQuery {
    private final JdbcTemplate jdbc;
    private final UUID branch;
    private final RestClient client;
    BillingQuoteDispatcher(JdbcTemplate jdbc, @Value("${oficina.billing-quote.branch-id}") UUID branch,
            @Value("${oficina.billing-quote.url}") String url, @Value("${oficina.billing-quote.service-token}") String token) {
        this.jdbc = jdbc; this.branch = branch;
        if (token.isBlank() || token.length() < 32) throw new IllegalArgumentException("Billing service token must have at least 32 characters");
        var factory = new JdkClientHttpRequestFactory(); factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).defaultHeader("X-Service-Token", token).build();
    }
    @Override public void schedule(UUID orderId, BigDecimal amount) {
        UUID saga = UUID.randomUUID(), correlation = UUID.randomUUID();
        jdbc.update("INSERT INTO billing_quote_requests (order_id,branch_id,saga_id,correlation_id,amount) VALUES (?,?,?,?,?)",
                orderId, branch, saga, correlation, amount);
    }
    @Override public void schedule(UUID orderId, BigDecimal amount, java.util.List<BillingQuoteDispatch.Item> items) {
        schedule(orderId, branch, amount, items);
    }
    @Override public void schedule(UUID orderId, UUID branchId, BigDecimal amount, java.util.List<BillingQuoteDispatch.Item> items) {
        if (branchId == null) throw new IllegalArgumentException("Billing branch is required");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Quote items are required");
        UUID saga = UUID.randomUUID(), correlation = UUID.randomUUID();
        try {
            String snapshot = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(items);
            jdbc.update("INSERT INTO billing_quote_requests (order_id,branch_id,saga_id,correlation_id,amount,items_snapshot) VALUES (?,?,?,?,?,?)",
                    orderId, branchId, saga, correlation, amount, snapshot);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Cannot serialize quote items", e); }
    }
    @Override public java.util.Optional<BillingCheckoutQuery.Checkout> findByOrderId(UUID orderId) {
        var quoteIds = jdbc.query("SELECT quote_id FROM billing_saga WHERE order_id=? ORDER BY updated_at DESC LIMIT 1",
                (rs, row) -> rs.getObject(1, UUID.class), orderId);
        if (quoteIds.isEmpty()) return java.util.Optional.empty();
        try {
            var result = client.get().uri("/api/quotes/{quote}/checkout", quoteIds.get(0)).retrieve().body(CheckoutResponse.class);
            if (result == null) return java.util.Optional.empty();
            return java.util.Optional.of(new BillingCheckoutQuery.Checkout(result.attemptId(), result.state(), result.checkoutUrl(), result.sandboxUrl()));
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound ignored) {
            return java.util.Optional.empty();
        }
    }
    @Scheduled(fixedDelayString="${oficina.billing-quote.delay-ms:5000}")
    @Transactional
    public void deliver() {
        var pending = jdbc.query("SELECT order_id,branch_id,saga_id,correlation_id,amount,items_snapshot FROM billing_quote_requests WHERE delivered_at IS NULL ORDER BY created_at LIMIT 20 FOR UPDATE SKIP LOCKED",
                (rs, row) -> new PendingQuote(new QuoteRequest(rs.getObject(2, UUID.class), rs.getObject(1, UUID.class), rs.getBigDecimal(5), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class)), rs.getString(6)));
        for (var request : pending) {
            if (request.snapshot() == null) client.post().uri("/api/quotes").body(request.quote()).retrieve().toBodilessEntity();
            else {
                try {
                    var items = new com.fasterxml.jackson.databind.ObjectMapper().readTree(request.snapshot());
                    var quote = request.quote();
                    client.post().uri("/api/quotes/itemized").body(new ItemizedRequest(quote.branchId(), quote.orderId(), quote.sagaId(), quote.correlationId(), items)).retrieve().toBodilessEntity();
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Invalid quote item snapshot", e); }
            }
            jdbc.update("UPDATE billing_quote_requests SET delivered_at=CURRENT_TIMESTAMP WHERE order_id=?", request.quote().orderId());
        }
    }
    private record QuoteRequest(UUID branchId, UUID orderId, BigDecimal amount, UUID sagaId, UUID correlationId) { }
    private record PendingQuote(QuoteRequest quote, String snapshot) { }
    private record ItemizedRequest(UUID branchId, UUID orderId, UUID sagaId, UUID correlationId, com.fasterxml.jackson.databind.JsonNode items) { }
    private record CheckoutResponse(UUID attemptId, String state, String checkoutUrl, String sandboxUrl) { }
}
