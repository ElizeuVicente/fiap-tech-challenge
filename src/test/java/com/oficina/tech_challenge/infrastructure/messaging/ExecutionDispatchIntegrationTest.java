package com.oficina.tech_challenge.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.oficina.tech_challenge.application.services.GerenciadorOrdemServico;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ExecutionDispatchIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Test void completionIsRetriedAndAppliedOnlyForMatchingIdentity() throws Exception {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        var jdbc = new JdbcTemplate(source);
        UUID branch = UUID.randomUUID(), order = UUID.randomUUID();
        var correct = new AtomicBoolean(false);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String returnedBranch = correct.get() ? branch.toString() : UUID.randomUUID().toString();
            byte[] body = ("{\"branchId\":\"" + returnedBranch + "\",\"orderId\":\"" + order + "\",\"status\":\"COMPLETED\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var orders = mock(GerenciadorOrdemServico.class);
            var factory = new DefaultListableBeanFactory(); factory.registerSingleton("orders", orders);
            var dispatch = new ExecutionDispatch(jdbc, branch, "http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-token-01234567890123456789012345", factory.getBeanProvider(GerenciadorOrdemServico.class),
                    new TransactionTemplate(new DataSourceTransactionManager(source)));
            jdbc.update("INSERT INTO execution_outbox(order_id, branch_id, delivered_at, diagnosis_received_at) VALUES (?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", order, branch);
            dispatch.receiveCompletion();
            verify(orders, never()).registrarConclusaoExecucao(order);
            correct.set(true);
            dispatch.receiveCompletion(); dispatch.receiveCompletion();
            verify(orders, times(1)).registrarConclusaoExecucao(order);
            assertEquals(1, jdbc.queryForObject("select count(*) from execution_outbox where order_id=? and completion_received_at is not null", Integer.class, order));
        } finally { server.stop(0); }
    }
    @Test void retriesUnavailableOrderAndContinuesOtherOrders() throws Exception {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        var jdbc = new JdbcTemplate(source);
        UUID branch = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        var unavailable = new AtomicBoolean(true);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (!"test-token-01234567890123456789012345".equals(exchange.getRequestHeaders().getFirst("X-Service-Token"))) {
                exchange.sendResponseHeaders(401, -1);
            } else if (path.endsWith(first.toString()) && unavailable.get()) {
                exchange.sendResponseHeaders(503, -1);
            } else {
                UUID id = path.endsWith(first.toString()) ? first : second;
                byte[] body = ("{\"branchId\":\"" + branch + "\",\"orderId\":\"" + id + "\",\"diagnosis\":\"Freio\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        try {
            var orders = mock(GerenciadorOrdemServico.class);
            var factory = new DefaultListableBeanFactory(); factory.registerSingleton("orders", orders);
            var dispatch = new ExecutionDispatch(jdbc, branch, "http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-token-01234567890123456789012345", factory.getBeanProvider(GerenciadorOrdemServico.class),
                    new TransactionTemplate(new DataSourceTransactionManager(source)));
            jdbc.update("INSERT INTO execution_outbox(order_id, branch_id, delivered_at) VALUES (?,?,CURRENT_TIMESTAMP),(?,?,CURRENT_TIMESTAMP)", first, branch, second, branch);
            dispatch.receiveDiagnosis();
            verify(orders).registrarDiagnosticoExecucao(second, "Freio");
            verify(orders, never()).registrarDiagnosticoExecucao(first, "Freio");
            unavailable.set(false);
            dispatch.receiveDiagnosis();
            dispatch.receiveDiagnosis();
            verify(orders, times(1)).registrarDiagnosticoExecucao(first, "Freio");
            verify(orders, times(1)).registrarDiagnosticoExecucao(second, "Freio");
            assertEquals(2, jdbc.queryForObject("select count(*) from execution_outbox where diagnosis_received_at is not null", Integer.class));
        } finally { server.stop(0); }
    }
}
