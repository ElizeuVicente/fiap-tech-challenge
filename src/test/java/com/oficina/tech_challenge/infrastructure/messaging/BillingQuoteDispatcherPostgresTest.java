package com.oficina.tech_challenge.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class BillingQuoteDispatcherPostgresTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Test void sendsFrozenItemizedQuoteAndRetriesSameSnapshot() throws Exception {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).load().migrate(); var jdbc = new JdbcTemplate(source);
        var attempts = new AtomicInteger(); var first = new AtomicReference<String>(); var second = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/quotes/itemized", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!first.compareAndSet(null, body)) second.set(body);
            exchange.sendResponseHeaders(attempts.incrementAndGet() == 1 ? 503 : 201, -1); exchange.close();
        });
        server.start();
        try {
            UUID branch = UUID.randomUUID(), order = UUID.randomUUID(), part = UUID.randomUUID();
            var dispatcher = new BillingQuoteDispatcher(jdbc, branch, "http://127.0.0.1:" + server.getAddress().getPort(), "local-test-token-01234567890123456789");
            dispatcher.schedule(order, new BigDecimal("30.00"), java.util.List.of(new com.oficina.tech_challenge.application.interfaces.BillingQuoteDispatch.Item("PART", part, 2, new BigDecimal("15.00"))));
            assertThrows(org.springframework.web.client.HttpServerErrorException.class, dispatcher::deliver);
            dispatcher.deliver(); dispatcher.deliver();
            assertEquals(first.get(), second.get()); assertEquals(2, attempts.get());
            var body = new ObjectMapper().readTree(first.get());
            assertEquals(part.toString(), body.path("items").get(0).path("referenceId").asText());
            assertEquals(2, body.path("items").get(0).path("quantity").asInt());
        } finally { server.stop(0); }
    }
    @Test void pendingRequestIsRetriedWithSameIdentifiersAndAmount() throws Exception {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        var jdbc = new JdbcTemplate(source);
        var attempts = new AtomicInteger();
        var firstBody = new AtomicReference<String>();
        var secondBody = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/quotes", exchange -> {
            if (!"local-test-token-01234567890123456789".equals(exchange.getRequestHeaders().getFirst("X-Service-Token"))) {
                exchange.sendResponseHeaders(401, -1); exchange.close(); return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!firstBody.compareAndSet(null, body)) secondBody.set(body);
            int code = attempts.incrementAndGet() == 1 ? 503 : 201;
            exchange.sendResponseHeaders(code, -1); exchange.close();
        });
        server.start();
        try {
            UUID branch = UUID.randomUUID(), order = UUID.randomUUID();
            var dispatcher = new BillingQuoteDispatcher(jdbc, branch, "http://127.0.0.1:" + server.getAddress().getPort(), "local-test-token-01234567890123456789");
            dispatcher.schedule(order, new BigDecimal("42.50"));
            assertThrows(org.springframework.web.client.HttpServerErrorException.class, dispatcher::deliver);
            assertEquals(0, jdbc.queryForObject("select count(*) from billing_quote_requests where delivered_at is not null", Integer.class));
            dispatcher.deliver(); dispatcher.deliver();
            assertEquals(2, attempts.get());
            assertEquals(firstBody.get(), secondBody.get());
            var json = new ObjectMapper().readTree(firstBody.get());
            assertEquals(order.toString(), json.get("orderId").asText());
            assertEquals(branch.toString(), json.get("branchId").asText());
            assertEquals(0, new BigDecimal("42.50").compareTo(json.get("amount").decimalValue()));
            assertEquals(1, jdbc.queryForObject("select count(*) from billing_quote_requests where delivered_at is not null", Integer.class));
        } finally { server.stop(0); }
    }
    @Test void checkoutIsReadThroughBillingApiWithoutSharingItsDatabase() throws Exception {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).load().migrate(); var jdbc = new JdbcTemplate(source);
        UUID branch = UUID.randomUUID(), order = UUID.randomUUID(), saga = UUID.randomUUID(), quote = UUID.randomUUID(), attempt = UUID.randomUUID();
        jdbc.update("INSERT INTO billing_saga(saga_id,order_id,branch_id,correlation_id,quote_id,status) VALUES (?,?,?,?,?,'APROVADA')",
                saga, order, branch, saga, quote);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/quotes/", exchange -> {
            assertEquals("local-test-token-01234567890123456789", exchange.getRequestHeaders().getFirst("X-Service-Token"));
            byte[] body = ("{\"attemptId\":\"" + attempt + "\",\"state\":\"READY\",\"checkoutUrl\":\"https://www.mercadopago.com.br/checkout/test\",\"sandboxUrl\":null}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var dispatcher = new BillingQuoteDispatcher(jdbc, branch, "http://127.0.0.1:" + server.getAddress().getPort(), "local-test-token-01234567890123456789");
            var result = dispatcher.findByOrderId(order).orElseThrow();
            assertEquals(attempt, result.attemptId()); assertEquals("READY", result.state());
            assertTrue(result.checkoutUrl().contains("mercadopago.com.br"));
            assertTrue(dispatcher.findByOrderId(UUID.randomUUID()).isEmpty());
        } finally { server.stop(0); }
    }
}
