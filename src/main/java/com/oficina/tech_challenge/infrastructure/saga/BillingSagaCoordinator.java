package com.oficina.tech_challenge.infrastructure.saga;

import com.oficina.tech_challenge.application.dtos.NotificacaoOrcamentoCommand;
import com.oficina.tech_challenge.domain.entities.DecisaoOrcamento;
import com.oficina.tech_challenge.infrastructure.messaging.BillingEvent;
import com.oficina.tech_challenge.infrastructure.messaging.BillingInbox;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the local OS side of the quote Saga. The inbox insert, saga transition and
 * compensation command are committed together, so a broker redelivery is safe.
 */
@Service
public class BillingSagaCoordinator {
    private final BillingInbox inbox;
    private final JdbcTemplate jdbc;
    private final BillingOrderDecisionApplier orderDecisionApplier;
    @org.springframework.beans.factory.annotation.Value("${oficina.billing-quote.enabled:false}")
    private boolean requireRegisteredRequest;

    public BillingSagaCoordinator(BillingInbox inbox, JdbcTemplate jdbc, BillingOrderDecisionApplier orderDecisionApplier) {
        this.inbox = inbox;
        this.jdbc = jdbc;
        this.orderDecisionApplier = orderDecisionApplier;
    }

    @Transactional
    public void receive(BillingEvent event) {
        if (requireRegisteredRequest) validateRequest(event);
        if (!inbox.receive(event)) return;
        switch (event.eventType()) {
            case "OrcamentoGerado" -> registrarOrcamento(event);
            case "OrcamentoAprovado" -> aplicarDecisao(event, DecisaoOrcamento.APROVADO);
            case "OrcamentoRecusado" -> aplicarDecisao(event, DecisaoOrcamento.RECUSADO);
            case "PagamentoConfirmado" -> aplicarPagamento(event, true);
            case "PagamentoRecusado" -> aplicarPagamento(event, false);
            case "PagamentoEstornado", "PagamentoContestado" -> aplicarReversao(event);
            default -> throw new AmqpRejectAndDontRequeueException("Unsupported billing event");
        }
    }

    private void validateRequest(BillingEvent event) {
        String expectedStatus = switch (event.eventType()) {
            case "OrcamentoGerado" -> "PENDING";
            case "OrcamentoAprovado" -> "APPROVED";
            case "OrcamentoRecusado" -> "REJECTED";
            case "PagamentoConfirmado" -> "APPROVED";
            case "PagamentoRecusado" -> "REJECTED";
            case "PagamentoEstornado" -> "REFUNDED";
            case "PagamentoContestado" -> "CHARGED_BACK";
            default -> throw new AmqpRejectAndDontRequeueException("Unsupported billing event");
        };
        var amount = event.payload().get("amount");
        if (amount == null || !amount.isNumber() || !expectedStatus.equals(event.payload().path("status").asText()))
            throw new AmqpRejectAndDontRequeueException("Invalid quote amount or status");
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM billing_quote_requests
                WHERE order_id=? AND branch_id=? AND saga_id=? AND correlation_id=? AND amount=?
                """, Integer.class, event.orderId(), event.branchId(), event.sagaId(), event.correlationId(), amount.decimalValue());
        if (count == null || count != 1)
            throw new AmqpRejectAndDontRequeueException("Billing event does not match a registered quote request");
    }

    private void aplicarPagamento(BillingEvent event, boolean approved) {
        UUID quote = requiredQuoteId(event);
        final UUID attempt;
        try { attempt = UUID.fromString(event.payload().path("attemptId").asText()); }
        catch (Exception e) { throw new AmqpRejectAndDontRequeueException("Invalid payment attempt ID", e); }
        String providerId = event.payload().path("providerPaymentId").asText();
        if (!providerId.matches("[0-9]{1,20}"))
            throw new AmqpRejectAndDontRequeueException("Invalid provider payment ID");
        var states = jdbc.query("SELECT status,payment_attempt_id,provider_payment_id FROM billing_saga WHERE saga_id=? AND order_id=? AND branch_id=? AND quote_id=? AND correlation_id=? FOR UPDATE",
                (rs, row) -> new PaymentState(rs.getString(1), rs.getObject(2, UUID.class), rs.getString(3)), event.sagaId(), event.orderId(), event.branchId(), quote, event.correlationId());
        if (states.size() != 1) throw new AmqpRejectAndDontRequeueException("Payment does not match saga");
        String next = approved ? "AGUARDANDO_ESTOQUE" : "PAGAMENTO_RECUSADO";
        PaymentState state = states.get(0);
        if (state.status().equals(next) && attempt.equals(state.attempt()) && providerId.equals(state.providerId())) return;
        if (!state.status().equals("APROVADA")) throw new AmqpRejectAndDontRequeueException("Payment received outside approved stage");
        jdbc.update("UPDATE billing_saga SET status=?,payment_attempt_id=?,provider_payment_id=?, updated_at=CURRENT_TIMESTAMP WHERE saga_id=?", next, attempt, providerId, event.sagaId());
    }
    private record PaymentState(String status, UUID attempt, String providerId) { }

    private void aplicarReversao(BillingEvent event) {
        UUID quote = requiredQuoteId(event);
        UUID attempt;
        try { attempt = UUID.fromString(event.payload().path("attemptId").asText()); }
        catch (Exception e) { throw new AmqpRejectAndDontRequeueException("Invalid payment attempt ID", e); }
        String provider = event.payload().path("providerPaymentId").asText();
        if (!provider.matches("[0-9]{1,20}")) throw new AmqpRejectAndDontRequeueException("Invalid provider payment ID");
        var states = jdbc.query("SELECT status,payment_attempt_id,provider_payment_id FROM billing_saga WHERE saga_id=? AND order_id=? AND branch_id=? AND quote_id=? AND correlation_id=? FOR UPDATE",
                (rs, row) -> new PaymentState(rs.getString(1), rs.getObject(2, UUID.class), rs.getString(3)), event.sagaId(), event.orderId(), event.branchId(), quote, event.correlationId());
        if (states.size() != 1 || !attempt.equals(states.get(0).attempt()) || !provider.equals(states.get(0).providerId()))
            throw new AmqpRejectAndDontRequeueException("Reversal does not match saga payment");
        String next = event.eventType().equals("PagamentoEstornado") ? "PAGAMENTO_ESTORNADO" : "PAGAMENTO_CONTESTADO";
        if (states.get(0).status().equals(next)) return;
        if (states.get(0).status().equals("REVERSAO_APOS_INICIO")) {
            String existing = jdbc.queryForObject("SELECT reversal_status FROM billing_saga WHERE saga_id=?", String.class, event.sagaId());
            if (next.equals(existing)) return;
            throw new AmqpRejectAndDontRequeueException("Conflicting payment reversal");
        }
        if (states.get(0).status().equals("LIBERACAO_PENDENTE")) {
            String existing = jdbc.queryForObject("SELECT reversal_status FROM billing_saga WHERE saga_id=?", String.class, event.sagaId());
            if (next.equals(existing)) return;
            throw new AmqpRejectAndDontRequeueException("Conflicting payment reversal");
        }
        if (states.get(0).status().equals("EM_EXECUCAO") || states.get(0).status().equals("EXECUCAO_FINALIZADA")) {
            jdbc.update("UPDATE billing_saga SET status='REVERSAO_APOS_INICIO',reversal_status=?,last_error='MANUAL_RECONCILIATION_REQUIRED',updated_at=CURRENT_TIMESTAMP WHERE saga_id=?", next, event.sagaId());
            return;
        }
        if (states.get(0).status().equals("ESTOQUE_RESERVADO") || states.get(0).status().equals("EXECUCAO_AUTORIZADA") || states.get(0).status().equals("AGUARDANDO_ESTOQUE")
                || states.get(0).status().equals("COMPENSACAO_FINANCEIRA_PENDENTE")) {
            jdbc.update("UPDATE billing_saga SET status='LIBERACAO_PENDENTE',reversal_status=?,updated_at=CURRENT_TIMESTAMP WHERE saga_id=?", next, event.sagaId());
            return;
        }
        throw new AmqpRejectAndDontRequeueException("Reversal received outside payment stage");
    }

    private void registrarOrcamento(BillingEvent event) {
        UUID quoteId = requiredQuoteId(event);
        int inserted = jdbc.update("""
                INSERT INTO billing_saga (saga_id, order_id, branch_id, correlation_id, quote_id, status)
                VALUES (?,?,?,?,?,'AGUARDANDO_DECISAO') ON CONFLICT (saga_id) DO NOTHING
                """, event.sagaId(), event.orderId(), event.branchId(), event.correlationId(), quoteId);
        if (inserted == 0) {
            Integer matches = jdbc.queryForObject("SELECT count(*) FROM billing_saga WHERE saga_id=? AND order_id=? AND branch_id=? AND quote_id=? AND correlation_id=?",
                    Integer.class, event.sagaId(), event.orderId(), event.branchId(), quoteId, event.correlationId());
            if (matches == null || matches != 1) {
                throw new AmqpRejectAndDontRequeueException("Saga identifiers conflict with an existing quote");
            }
        }
    }

    private void aplicarDecisao(BillingEvent event, DecisaoOrcamento decisao) {
        UUID quoteId = requiredQuoteId(event);
        String status = jdbc.query("SELECT status FROM billing_saga WHERE saga_id=? FOR UPDATE",
                rs -> rs.next() ? rs.getString(1) : null, event.sagaId());
        if (status == null) throw new AmqpRejectAndDontRequeueException("Decision received before quote creation");
        Integer matches = jdbc.queryForObject("SELECT count(*) FROM billing_saga WHERE saga_id=? AND order_id=? AND branch_id=? AND quote_id=? AND correlation_id=?",
                Integer.class, event.sagaId(), event.orderId(), event.branchId(), quoteId, event.correlationId());
        if (matches == null || matches != 1)
            throw new AmqpRejectAndDontRequeueException("Decision identifiers do not match saga");
        if (!"AGUARDANDO_DECISAO".equals(status)) {
            if (("APROVADA".equals(status) && decisao == DecisaoOrcamento.APROVADO)
                    || ("RECUSADA".equals(status) && decisao == DecisaoOrcamento.RECUSADO)
                    || ("COMPENSACAO_PENDENTE".equals(status) && decisao == DecisaoOrcamento.APROVADO)) return;
            throw new AmqpRejectAndDontRequeueException("Decision conflicts with saga state");
        }
        try {
            orderDecisionApplier.apply(event.orderId(), new NotificacaoOrcamentoCommand(
                    decisao, "BILLING_SAGA", LocalDateTime.now(), event.eventId().toString()));
            jdbc.update("UPDATE billing_saga SET status=?, decided_event_id=?, updated_at=CURRENT_TIMESTAMP WHERE saga_id=?",
                    decisao == DecisaoOrcamento.APROVADO ? "APROVADA" : "RECUSADA", event.eventId(), event.sagaId());
        } catch (RuntimeException failure) {
            if (decisao == DecisaoOrcamento.APROVADO) {
                solicitarCompensacao(event, quoteId, failure.getMessage());
                return;
            }
            jdbc.update("UPDATE billing_saga SET status='FALHA', last_error=?, updated_at=CURRENT_TIMESTAMP WHERE saga_id=?",
                    safeMessage(failure), event.sagaId());
        }
    }

    private void solicitarCompensacao(BillingEvent event, UUID quoteId, String reason) {
        jdbc.update("UPDATE billing_saga SET status='COMPENSACAO_PENDENTE', last_error=?, updated_at=CURRENT_TIMESTAMP WHERE saga_id=?",
                safeMessage(reason), event.sagaId());
        jdbc.update("""
                INSERT INTO billing_command_outbox (command_id, saga_id, command_type, payload)
                VALUES (?,?, 'CancelarOrcamento', ?)
                ON CONFLICT (saga_id, command_type) DO NOTHING
                """, UUID.randomUUID(), event.sagaId(), "{\"quoteId\":\"" + quoteId + "\",\"reason\":\"OS_APPROVAL_FAILED\"}");
    }

    private UUID requiredQuoteId(BillingEvent event) {
        try { return UUID.fromString(event.payload().path("quoteId").asText()); }
        catch (Exception e) { throw new AmqpRejectAndDontRequeueException("Billing event has no valid quoteId", e); }
    }
    private String safeMessage(Object message) {
        String value = String.valueOf(message);
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
