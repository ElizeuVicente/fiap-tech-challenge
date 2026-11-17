package com.oficina.tech_challenge.infrastructure.saga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.tech_challenge.infrastructure.messaging.BillingEvent;
import com.oficina.tech_challenge.infrastructure.messaging.BillingInbox;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class BillingSagaCoordinatorPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Test void reversalAfterStartRequiresInterventionAndIsIdempotent() {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        var jdbc = new JdbcTemplate(source); var mapper = new ObjectMapper();
        var coordinator = new BillingSagaCoordinator(new BillingInbox(jdbc, mapper), jdbc, org.mockito.Mockito.mock(BillingOrderDecisionApplier.class));
        UUID saga = UUID.randomUUID(), order = UUID.randomUUID(), branch = UUID.randomUUID(), quote = UUID.randomUUID(), attempt = UUID.randomUUID();
        jdbc.update("INSERT INTO billing_saga(saga_id,order_id,branch_id,quote_id,correlation_id,status,payment_attempt_id,provider_payment_id) VALUES (?,?,?,?,?,'EM_EXECUCAO',?,'123456')", saga, order, branch, quote, saga, attempt);
        for (int i = 0; i < 2; i++) {
            String event = paymentEvent(mapper, saga, order, branch, quote, attempt).rawPayload().replace("PagamentoConfirmado", "PagamentoEstornado").replace("APPROVED", "REFUNDED");
            coordinator.receive(BillingEvent.parse(event.getBytes(StandardCharsets.UTF_8), mapper));
        }
        assertEquals("REVERSAO_APOS_INICIO", jdbc.queryForObject("select status from billing_saga where saga_id=?", String.class, saga));
        assertEquals("MANUAL_RECONCILIATION_REQUIRED", jdbc.queryForObject("select last_error from billing_saga where saga_id=?", String.class, saga));
    }

    @Test
    void confirmedPaymentWaitsForStockAndRejectsDifferentPayment() {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        var jdbc = new JdbcTemplate(source); var mapper = new ObjectMapper();
        var orders = org.mockito.Mockito.mock(BillingOrderDecisionApplier.class);
        var coordinator = new BillingSagaCoordinator(new BillingInbox(jdbc, mapper), jdbc, orders);
        UUID saga = UUID.randomUUID(), order = UUID.randomUUID(), branch = UUID.randomUUID(), quote = UUID.randomUUID(), attempt = UUID.randomUUID();
        coordinator.receive(event(mapper, UUID.randomUUID(), "OrcamentoGerado", saga, order, branch, quote));
        coordinator.receive(event(mapper, UUID.randomUUID(), "OrcamentoAprovado", saga, order, branch, quote));
        coordinator.receive(paymentEvent(mapper, saga, order, branch, quote, attempt));
        coordinator.receive(paymentEvent(mapper, saga, order, branch, quote, attempt));
        assertEquals("AGUARDANDO_ESTOQUE", jdbc.queryForObject("select status from billing_saga where saga_id=?", String.class, saga));
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class,
                () -> coordinator.receive(paymentEvent(mapper, saga, order, branch, quote, UUID.randomUUID())));
        var refund = paymentEvent(mapper, saga, order, branch, quote, attempt).rawPayload()
                .replace("PagamentoConfirmado", "PagamentoEstornado").replace("APPROVED", "REFUNDED");
        coordinator.receive(BillingEvent.parse(refund.getBytes(StandardCharsets.UTF_8), mapper));
        assertEquals("LIBERACAO_PENDENTE", jdbc.queryForObject("select status from billing_saga where saga_id=?", String.class, saga));
        coordinator.receive(BillingEvent.parse(refund.getBytes(StandardCharsets.UTF_8), mapper));
        verify(orders, times(1)).apply(any(), any());
    }

    private BillingEvent paymentEvent(ObjectMapper mapper, UUID saga, UUID order, UUID branch, UUID quote, UUID attempt) {
        String json = """
                {"eventId":"%s","eventType":"PagamentoConfirmado","schemaVersion":1,"occurredAt":"2026-11-06T12:00:00Z",
                 "sagaId":"%s","osId":"%s","filialId":"%s","correlationId":"%s",
                 "payload":{"quoteId":"%s","attemptId":"%s","providerPaymentId":"123456","amount":42.50,"status":"APPROVED"}}
                """.formatted(UUID.randomUUID(), saga, order, branch, saga, quote, attempt);
        return BillingEvent.parse(json.getBytes(StandardCharsets.UTF_8), mapper);
    }

    @Test
    void integratedModeAcceptsOnlyRegisteredQuoteWithMatchingAmountAndStatus() throws Exception {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        var jdbc = new JdbcTemplate(source);
        var mapper = new ObjectMapper();
        var orders = org.mockito.Mockito.mock(BillingOrderDecisionApplier.class);
        var coordinator = new BillingSagaCoordinator(new BillingInbox(jdbc, mapper), jdbc, orders);
        org.springframework.test.util.ReflectionTestUtils.setField(coordinator, "requireRegisteredRequest", true);
        UUID saga = UUID.randomUUID(), order = UUID.randomUUID(), branch = UUID.randomUUID(), quote = UUID.randomUUID();
        String template = """
                {"eventId":"%s","eventType":"OrcamentoGerado","schemaVersion":1,"occurredAt":"2026-11-06T12:00:00Z",
                 "sagaId":"%s","osId":"%s","filialId":"%s","correlationId":"%s",
                 "payload":{"quoteId":"%s","status":"%s","amount":%s}}
                """;
        java.util.function.BiFunction<String, String, BillingEvent> generated = (status, amount) -> BillingEvent.parse(
                template.formatted(UUID.randomUUID(), saga, order, branch, saga, quote, status, amount).getBytes(StandardCharsets.UTF_8), mapper);
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class, () -> coordinator.receive(generated.apply("PENDING", "42.50")));
        jdbc.update("INSERT INTO billing_quote_requests(order_id,branch_id,saga_id,correlation_id,amount) VALUES (?,?,?,?,?)",
                order, branch, saga, saga, new java.math.BigDecimal("42.50"));
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class, () -> coordinator.receive(generated.apply("PENDING", "42.51")));
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class, () -> coordinator.receive(generated.apply("APPROVED", "42.50")));
        assertEquals(0, jdbc.queryForObject("select count(*) from billing_inbox where saga_id=?", Integer.class, saga));
        coordinator.receive(generated.apply("PENDING", "42.50"));
        assertEquals("AGUARDANDO_DECISAO", jdbc.queryForObject("select status from billing_saga where saga_id=?", String.class, saga));
        org.mockito.Mockito.verifyNoInteractions(orders);
    }

    @Test
    void rejectsDecisionWithDifferentBranchOrderOrQuote() {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        var jdbc = new JdbcTemplate(source);
        var mapper = new ObjectMapper();
        var orders = org.mockito.Mockito.mock(BillingOrderDecisionApplier.class);
        var coordinator = new BillingSagaCoordinator(new BillingInbox(jdbc, mapper), jdbc, orders);
        UUID saga = UUID.randomUUID(), order = UUID.randomUUID(), branch = UUID.randomUUID(), quote = UUID.randomUUID();
        coordinator.receive(event(mapper, UUID.randomUUID(), "OrcamentoGerado", saga, order, branch, quote));
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class,
                () -> coordinator.receive(event(mapper, UUID.randomUUID(), "OrcamentoAprovado", saga, order, UUID.randomUUID(), quote)));
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class,
                () -> coordinator.receive(event(mapper, UUID.randomUUID(), "OrcamentoAprovado", saga, UUID.randomUUID(), branch, quote)));
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class,
                () -> coordinator.receive(event(mapper, UUID.randomUUID(), "OrcamentoAprovado", saga, order, branch, UUID.randomUUID())));
        org.mockito.Mockito.verifyNoInteractions(orders);
        assertEquals("AGUARDANDO_DECISAO", jdbc.queryForObject("select status from billing_saga where saga_id=?", String.class, saga));
    }

    @Test
    void recordsSagaAndAppliesAnApprovalOnlyOnce() {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        var jdbc = new JdbcTemplate(source);
        var mapper = new ObjectMapper();
        var orders = org.mockito.Mockito.mock(BillingOrderDecisionApplier.class);
        var coordinator = new BillingSagaCoordinator(new BillingInbox(jdbc, mapper), jdbc, orders);
        UUID saga = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID quote = UUID.randomUUID();

        coordinator.receive(event(mapper, UUID.randomUUID(), "OrcamentoGerado", saga, order, branch, quote));
        coordinator.receive(event(mapper, UUID.randomUUID(), "OrcamentoAprovado", saga, order, branch, quote));
        coordinator.receive(event(mapper, UUID.randomUUID(), "OrcamentoAprovado", saga, order, branch, quote));

        assertEquals("APROVADA", jdbc.queryForObject("select status from billing_saga where saga_id=?", String.class, saga));
        assertEquals(3, jdbc.queryForObject("select count(*) from billing_inbox where saga_id=?", Integer.class, saga));
        verify(orders, times(1)).apply(any(), any());
    }

    @Test
    void writesCompensationCommandWhenApprovalCannotBeApplied() {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        var jdbc = new JdbcTemplate(source);
        var mapper = new ObjectMapper();
        var orders = org.mockito.Mockito.mock(BillingOrderDecisionApplier.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("insufficient stock")).when(orders).apply(any(), any());
        var coordinator = new BillingSagaCoordinator(new BillingInbox(jdbc, mapper), jdbc, orders);
        UUID saga = UUID.randomUUID(); UUID order = UUID.randomUUID(); UUID branch = UUID.randomUUID(); UUID quote = UUID.randomUUID();

        coordinator.receive(event(mapper, UUID.randomUUID(), "OrcamentoGerado", saga, order, branch, quote));
        coordinator.receive(event(mapper, UUID.randomUUID(), "OrcamentoAprovado", saga, order, branch, quote));

        assertEquals("COMPENSACAO_PENDENTE", jdbc.queryForObject("select status from billing_saga where saga_id=?", String.class, saga));
        assertEquals(1, jdbc.queryForObject("select count(*) from billing_command_outbox where saga_id=?", Integer.class, saga));
    }

    private BillingEvent event(ObjectMapper mapper, UUID eventId, String type, UUID saga, UUID order, UUID branch, UUID quote) {
        String json = """
                {"eventId":"%s","eventType":"%s","schemaVersion":1,"occurredAt":"2026-11-06T12:00:00Z",
                "sagaId":"%s","osId":"%s","filialId":"%s","correlationId":"%s",
                "payload":{"quoteId":"%s","status":"APPROVED"}}
                """.formatted(eventId, type, saga, order, branch, saga, quote);
        return BillingEvent.parse(json.getBytes(StandardCharsets.UTF_8), mapper);
    }
}
