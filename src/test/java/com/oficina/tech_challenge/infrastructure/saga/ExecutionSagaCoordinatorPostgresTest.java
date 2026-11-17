package com.oficina.tech_challenge.infrastructure.saga;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.oficina.tech_challenge.application.services.GerenciadorOrdemServico;
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
class ExecutionSagaCoordinatorPostgresTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Test void authorizesOnlyReservedPaymentAndRecoversLostStartResponse() throws Exception {
        var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(ds).load().migrate(); var jdbc = new JdbcTemplate(ds);
        UUID saga = UUID.randomUUID(), order = UUID.randomUUID(), branch = UUID.randomUUID(), quote = UUID.randomUUID(), part = UUID.randomUUID(), attempt = UUID.randomUUID();
        String snapshot = "[{\"type\":\"PART\",\"referenceId\":\"" + part + "\",\"quantity\":2,\"unitPrice\":10.00}]";
        jdbc.update("INSERT INTO billing_quote_requests(order_id,branch_id,saga_id,correlation_id,amount,items_snapshot) VALUES (?,?,?,?,?,?)", order, branch, saga, saga, 20, snapshot);
        jdbc.update("INSERT INTO billing_saga(saga_id,order_id,branch_id,correlation_id,quote_id,status,payment_attempt_id) VALUES (?,?,?,?,?,'ESTOQUE_RESERVADO',?)", saga, order, branch, saga, quote, attempt);
        var starts = new AtomicInteger(); var authorization = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/authorization")) {
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(request.contains(saga.toString())); assertTrue(request.contains(attempt.toString())); authorization.incrementAndGet();
            }
            if (path.endsWith("/start")) { starts.incrementAndGet(); exchange.sendResponseHeaders(503, -1); exchange.close(); return; }
            String state = starts.get() > 0 ? "IN_PROGRESS" : "READY";
            byte[] body = ("{\"orderId\":\"" + order + "\",\"branchId\":\"" + branch + "\",\"status\":\"" + state + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var orders = mock(GerenciadorOrdemServico.class);
            var worker = new ExecutionSagaCoordinator(jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)), orders,
                    "http://127.0.0.1:" + server.getAddress().getPort(), "local-test-token-01234567890123456789");
            worker.processPending(); assertEquals("EXECUCAO_AUTORIZADA", jdbc.queryForObject("SELECT status FROM billing_saga WHERE saga_id=?", String.class, saga));
            worker.processPending(); assertEquals("EXECUCAO_AUTORIZADA", jdbc.queryForObject("SELECT status FROM billing_saga WHERE saga_id=?", String.class, saga));
            worker.processPending(); worker.processPending();
            assertEquals("EM_EXECUCAO", jdbc.queryForObject("SELECT status FROM billing_saga WHERE saga_id=?", String.class, saga));
            assertEquals(1, authorization.get()); assertEquals(1, starts.get()); verify(orders, times(1)).registrarInicioExecucaoConfirmada(order);
        } finally { server.stop(0); }
    }
}
