package com.oficina.tech_challenge.infrastructure.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

/** Reserves the approved item snapshot; a reversal must release before it is considered handled. */
@Component
@ConditionalOnProperty(name="oficina.stock-saga.enabled", havingValue="true")
class StockReservationCoordinator {
    private static final Logger log = LoggerFactory.getLogger(StockReservationCoordinator.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final RestClient billing;
    private final RestClient execution;
    private final ObjectMapper json = new ObjectMapper();
    StockReservationCoordinator(JdbcTemplate jdbc, TransactionTemplate transactions,
            @Value("${oficina.billing-quote.url}") String billingUrl, @Value("${oficina.billing-quote.service-token}") String billingToken,
            @Value("${oficina.execution.url}") String executionUrl, @Value("${oficina.execution.service-token}") String executionToken) {
        this.jdbc = jdbc; this.transactions = transactions;
        this.billing = client(billingUrl, billingToken); this.execution = client(executionUrl, executionToken);
    }
    private RestClient client(String url, String token) {
        if (token == null || token.length() < 32) throw new IllegalArgumentException("Service token must have at least 32 characters");
        var factory = new JdkClientHttpRequestFactory(); factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().baseUrl(url).requestFactory(factory).defaultHeader("X-Service-Token", token).build();
    }
    @Scheduled(fixedDelayString="${oficina.stock-saga.delay-ms:10000}")
    public void processPending() {
        var ids = jdbc.query("SELECT saga_id FROM billing_saga WHERE status IN ('AGUARDANDO_ESTOQUE','LIBERACAO_PENDENTE') ORDER BY updated_at LIMIT 20", (rs, i) -> rs.getObject(1, UUID.class));
        for (UUID id : ids) {
            try { transactions.executeWithoutResult(status -> process(id)); }
            catch (RuntimeException e) {
                log.warn("Stock saga operation failed for {}: {}", id, e.getClass().getSimpleName());
                jdbc.update("UPDATE billing_saga SET last_error=?,updated_at=CURRENT_TIMESTAMP WHERE saga_id=? AND status IN ('AGUARDANDO_ESTOQUE','LIBERACAO_PENDENTE')", e.getClass().getSimpleName(), id);
            }
        }
    }
    @Scheduled(fixedDelayString="${oficina.stock-saga.refund-delay-ms:10000}")
    public void deliverRefundRequests() {
        var ids = jdbc.query("SELECT r.saga_id FROM billing_refund_requests r JOIN billing_saga s ON s.saga_id=r.saga_id WHERE r.delivered_at IS NULL AND s.status='COMPENSACAO_FINANCEIRA_PENDENTE' ORDER BY r.last_attempt_at NULLS FIRST,r.created_at LIMIT 20", (rs, i) -> rs.getObject(1, UUID.class));
        for (UUID id : ids) {
            try { transactions.executeWithoutResult(status -> deliverRefund(id)); }
            catch (RuntimeException e) {
                log.warn("Refund request delivery failed for {}: {}", id, e.getClass().getSimpleName());
                jdbc.update("UPDATE billing_refund_requests SET attempts=attempts+1,last_attempt_at=CURRENT_TIMESTAMP WHERE saga_id=? AND delivered_at IS NULL", id);
            }
        }
    }
    private void deliverRefund(UUID id) {
        var rows = jdbc.query("""
                SELECT r.quote_id,r.payment_attempt_id,r.provider_payment_id,r.reason
                FROM billing_refund_requests r JOIN billing_saga s ON s.saga_id=r.saga_id
                WHERE r.saga_id=? AND r.delivered_at IS NULL AND s.status='COMPENSACAO_FINANCEIRA_PENDENTE'
                FOR UPDATE OF s,r SKIP LOCKED
                """, (rs, i) -> new RefundRequest(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4)), id);
        if (rows.isEmpty()) return;
        billing.put().uri("/api/refund-requests/{saga}", id).body(rows.get(0)).retrieve().toBodilessEntity();
        jdbc.update("UPDATE billing_refund_requests SET delivered_at=CURRENT_TIMESTAMP,last_attempt_at=CURRENT_TIMESTAMP,attempts=attempts+1 WHERE saga_id=?", id);
    }
    private void process(UUID id) {
        var rows = jdbc.query("SELECT s.status,s.branch_id,s.quote_id,s.reversal_status,r.items_snapshot,s.order_id FROM billing_saga s LEFT JOIN billing_quote_requests r ON r.saga_id=s.saga_id WHERE s.saga_id=? FOR UPDATE OF s SKIP LOCKED",
                (rs, i) -> new Saga(rs.getString(1), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getObject(6, UUID.class)), id);
        if (rows.isEmpty()) return;
        Saga saga = rows.get(0);
        if (saga.status().equals("LIBERACAO_PENDENTE")) {
            if (!Set.of("PAGAMENTO_ESTORNADO", "PAGAMENTO_CONTESTADO").contains(saga.reversal())) throw new IllegalStateException("Missing reversal outcome");
            execution.post().uri("/api/branches/{branch}/work-orders/{order}/authorization/cancel?reservationId={reservation}", saga.branch(), saga.order(), id).retrieve().toBodilessEntity();
            jdbc.update("UPDATE billing_saga SET status=?,last_error=NULL,updated_at=CURRENT_TIMESTAMP WHERE saga_id=?", saga.reversal(), id);
        } else if (saga.status().equals("AGUARDANDO_ESTOQUE")) {
            List<Item> items = approvedParts(saga);
            try {
                execution.put().uri("/api/branches/{branch}/stock-reservations/{reservation}", saga.branch(), id)
                        .body(Map.of("items", items)).retrieve().toBodilessEntity();
            } catch (org.springframework.web.client.HttpClientErrorException.UnprocessableEntity insufficient) {
                int inserted = jdbc.update("""
                        INSERT INTO billing_refund_requests(saga_id,quote_id,payment_attempt_id,provider_payment_id,reason)
                        SELECT saga_id,quote_id,payment_attempt_id,provider_payment_id,'INSUFFICIENT_STOCK'
                        FROM billing_saga WHERE saga_id=? AND payment_attempt_id IS NOT NULL AND provider_payment_id IS NOT NULL
                        ON CONFLICT (saga_id) DO NOTHING
                        """, id);
                if (inserted != 1) throw new IllegalStateException("Cannot record financial compensation");
                jdbc.update("UPDATE billing_saga SET status='COMPENSACAO_FINANCEIRA_PENDENTE',last_error='INSUFFICIENT_STOCK',updated_at=CURRENT_TIMESTAMP WHERE saga_id=?", id);
                return;
            }
            jdbc.update("UPDATE billing_saga SET status='ESTOQUE_RESERVADO',last_error=NULL,updated_at=CURRENT_TIMESTAMP WHERE saga_id=?", id);
        }
    }
    private List<Item> approvedParts(Saga saga) {
        if (saga.snapshot() == null) throw new IllegalStateException("Legacy quote has no item snapshot");
        try {
            JsonNode expected = json.readTree(saga.snapshot());
            JsonNode actual = billing.get().uri("/api/quotes/{id}/items", saga.quote()).retrieve().body(JsonNode.class);
            if (!expected.isArray() || actual == null || !actual.isArray() || expected.size() != actual.size() || expected.isEmpty())
                throw new IllegalStateException("Quote item snapshot mismatch");
            Map<UUID, Integer> parts = new LinkedHashMap<>();
            for (int i = 0; i < expected.size(); i++) {
                JsonNode a = expected.get(i), b = actual.get(i);
                if (!a.path("type").asText().equals(b.path("itemType").asText())
                        || !a.path("referenceId").asText().equals(b.path("referenceId").asText())
                        || a.path("quantity").asInt() != b.path("quantity").asInt()
                        || !a.path("unitPrice").isNumber() || !b.path("unitPrice").isNumber()
                        || a.path("unitPrice").decimalValue().compareTo(b.path("unitPrice").decimalValue()) != 0)
                    throw new IllegalStateException("Quote item snapshot mismatch");
                if (a.path("type").asText().equals("PART")) {
                    int quantity = a.path("quantity").asInt();
                    if (quantity <= 0) throw new IllegalStateException("Invalid quantity");
                    parts.merge(UUID.fromString(a.path("referenceId").asText()), quantity, Math::addExact);
                }
            }
            return parts.entrySet().stream().map(e -> new Item(e.getKey(), e.getValue())).toList();
        } catch (java.io.IOException | IllegalArgumentException e) { throw new IllegalStateException("Invalid quote item snapshot", e); }
    }
    private record Saga(String status, UUID branch, UUID quote, String reversal, String snapshot, UUID order) { }
    private record Item(UUID partId, int quantity) { }
    private record RefundRequest(UUID quoteId, UUID paymentAttemptId, String providerPaymentId, String reason) { }
}
