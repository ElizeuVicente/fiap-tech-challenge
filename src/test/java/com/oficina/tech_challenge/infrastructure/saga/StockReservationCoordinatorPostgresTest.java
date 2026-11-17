package com.oficina.tech_challenge.infrastructure.saga;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class StockReservationCoordinatorPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Test void serviceOnlyQuoteCreatesAnEmptyReservation() throws Exception {
        var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(ds).load().migrate(); var jdbc = new JdbcTemplate(ds);
        UUID saga = UUID.randomUUID(), order = UUID.randomUUID(), branch = UUID.randomUUID(), quote = UUID.randomUUID(), service = UUID.randomUUID();
        String snapshot = "[{\"type\":\"SERVICE\",\"referenceId\":\"" + service + "\",\"quantity\":1,\"unitPrice\":30.00}]";
        jdbc.update("INSERT INTO billing_quote_requests(order_id,branch_id,saga_id,correlation_id,amount,items_snapshot) VALUES (?,?,?,?,?,?)", order, branch, saga, saga, 30, snapshot);
        jdbc.update("INSERT INTO billing_saga(saga_id,order_id,branch_id,correlation_id,quote_id,status) VALUES (?,?,?,?,?,'AGUARDANDO_ESTOQUE')", saga, order, branch, saga, quote);
        var captured = new java.util.concurrent.atomic.AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/items")) {
                byte[] body = ("[{\"itemType\":\"SERVICE\",\"referenceId\":\"" + service + "\",\"quantity\":1,\"unitPrice\":30.00}]").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
            } else { captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)); exchange.sendResponseHeaders(204, -1); }
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            var worker = new StockReservationCoordinator(jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)), url,
                    "billing-test-token-0123456789012345", url, "execution-test-token-01234567890123");
            worker.processPending();
            assertEquals("ESTOQUE_RESERVADO", jdbc.queryForObject("SELECT status FROM billing_saga WHERE saga_id=?", String.class, saga));
            assertTrue(new com.fasterxml.jackson.databind.ObjectMapper().readTree(captured.get()).path("items").isEmpty());
        } finally { server.stop(0); }
    }
    @Test void insufficientStockRecordsOneFinancialCompensationRequest() throws Exception {
        var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(ds).load().migrate(); var jdbc = new JdbcTemplate(ds);
        UUID saga = UUID.randomUUID(), order = UUID.randomUUID(), branch = UUID.randomUUID(), quote = UUID.randomUUID(), part = UUID.randomUUID(), attempt = UUID.randomUUID();
        String snapshot = "[{\"type\":\"PART\",\"referenceId\":\"" + part + "\",\"quantity\":2,\"unitPrice\":15.00}]";
        jdbc.update("INSERT INTO billing_quote_requests(order_id,branch_id,saga_id,correlation_id,amount,items_snapshot) VALUES (?,?,?,?,?,?)", order, branch, saga, saga, 30, snapshot);
        jdbc.update("INSERT INTO billing_saga(saga_id,order_id,branch_id,correlation_id,quote_id,status,payment_attempt_id,provider_payment_id) VALUES (?,?,?,?,?,'AGUARDANDO_ESTOQUE',?,'123456')", saga, order, branch, saga, quote, attempt);
        var deliveries = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/items")) {
                byte[] body = ("[{\"itemType\":\"PART\",\"referenceId\":\"" + part + "\",\"quantity\":2,\"unitPrice\":15.00}]").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
            } else if (exchange.getRequestURI().getPath().contains("/refund-requests/")) {
                exchange.sendResponseHeaders(deliveries.incrementAndGet() == 1 ? 503 : 204, -1);
            } else exchange.sendResponseHeaders(422, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            var worker = new StockReservationCoordinator(jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)), url,
                    "billing-test-token-0123456789012345", url, "execution-test-token-01234567890123");
            worker.processPending(); worker.processPending();
            assertEquals("COMPENSACAO_FINANCEIRA_PENDENTE", jdbc.queryForObject("SELECT status FROM billing_saga WHERE saga_id=?", String.class, saga));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM billing_refund_requests WHERE saga_id=? AND payment_attempt_id=?", Integer.class, saga, attempt));
            worker.deliverRefundRequests(); worker.deliverRefundRequests(); worker.deliverRefundRequests();
            assertEquals(2, deliveries.get());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM billing_refund_requests WHERE saga_id=? AND delivered_at IS NOT NULL", Integer.class, saga));
            assertEquals("COMPENSACAO_FINANCEIRA_PENDENTE", jdbc.queryForObject("SELECT status FROM billing_saga WHERE saga_id=?", String.class, saga));
        } finally { server.stop(0); }
    }
    @Test void retriesReservationAndConfirmsReleaseBeforeCompletingReversal() throws Exception {
        var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        var jdbc = new JdbcTemplate(ds);
        UUID saga = UUID.randomUUID(), order = UUID.randomUUID(), branch = UUID.randomUUID(), quote = UUID.randomUUID(), part = UUID.randomUUID();
        String snapshot = "[{\"type\":\"PART\",\"referenceId\":\"" + part + "\",\"quantity\":2,\"unitPrice\":15.00}]";
        jdbc.update("INSERT INTO billing_quote_requests(order_id,branch_id,saga_id,correlation_id,amount,items_snapshot) VALUES (?,?,?,?,?,?)", order, branch, saga, saga, 30, snapshot);
        jdbc.update("INSERT INTO billing_saga(saga_id,order_id,branch_id,correlation_id,quote_id,status) VALUES (?,?,?,?,?,'AGUARDANDO_ESTOQUE')", saga, order, branch, saga, quote);
        var attempts = new AtomicInteger(); var releases = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/items")) {
                byte[] body = ("[{\"itemType\":\"PART\",\"referenceId\":\"" + part + "\",\"quantity\":2,\"unitPrice\":15.00}]").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
            } else if (exchange.getRequestURI().getPath().endsWith("/authorization/cancel")) {
                exchange.sendResponseHeaders(releases.incrementAndGet() == 1 ? 503 : 204, -1);
            } else exchange.sendResponseHeaders(attempts.incrementAndGet() == 1 ? 503 : 204, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            var worker = new StockReservationCoordinator(jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)), url,
                    "billing-test-token-0123456789012345", url, "execution-test-token-01234567890123");
            worker.processPending();
            assertEquals("AGUARDANDO_ESTOQUE", jdbc.queryForObject("SELECT status FROM billing_saga WHERE saga_id=?", String.class, saga));
            worker.processPending();
            assertEquals("ESTOQUE_RESERVADO", jdbc.queryForObject("SELECT status FROM billing_saga WHERE saga_id=?", String.class, saga));
            jdbc.update("UPDATE billing_saga SET status='LIBERACAO_PENDENTE',reversal_status='PAGAMENTO_ESTORNADO' WHERE saga_id=?", saga);
            worker.processPending();
            assertEquals("LIBERACAO_PENDENTE", jdbc.queryForObject("SELECT status FROM billing_saga WHERE saga_id=?", String.class, saga));
            worker.processPending(); worker.processPending();
            assertEquals("PAGAMENTO_ESTORNADO", jdbc.queryForObject("SELECT status FROM billing_saga WHERE saga_id=?", String.class, saga));
            assertEquals(2, attempts.get()); assertEquals(2, releases.get());
        } finally { server.stop(0); }
    }
}
