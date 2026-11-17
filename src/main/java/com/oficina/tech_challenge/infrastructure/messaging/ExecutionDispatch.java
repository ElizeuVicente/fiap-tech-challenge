package com.oficina.tech_challenge.infrastructure.messaging;

import com.oficina.tech_challenge.application.interfaces.ExecutionOrderDispatch;
import com.oficina.tech_challenge.application.services.GerenciadorOrdemServico;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(name="oficina.execution.enabled", havingValue="true")
class ExecutionDispatch implements ExecutionOrderDispatch {
    private static final Logger log = LoggerFactory.getLogger(ExecutionDispatch.class);
    private final JdbcTemplate jdbc;
    private final UUID branch;
    private final RestClient client;
    private final org.springframework.beans.factory.ObjectProvider<GerenciadorOrdemServico> orders;
    private final TransactionTemplate transactions;

    ExecutionDispatch(JdbcTemplate jdbc, @Value("${oficina.execution.branch-id}") UUID branch,
            @Value("${oficina.execution.url}") String url, @Value("${oficina.execution.service-token}") String token,
            org.springframework.beans.factory.ObjectProvider<GerenciadorOrdemServico> orders, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.branch = branch;
        this.orders = orders;
        this.transactions = transactions;
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(5));
        if (token.isBlank() || token.length() < 32) throw new IllegalArgumentException("Execution service token must have at least 32 characters");
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).defaultHeader("X-Service-Token", token).build();
    }
    @Scheduled(fixedDelayString="${oficina.execution.diagnosis-delay-ms:10000}")
    public void receiveDiagnosis() {
        var pending = jdbc.query("SELECT order_id FROM execution_outbox WHERE delivered_at IS NOT NULL AND diagnosis_received_at IS NULL ORDER BY diagnosis_checked_at NULLS FIRST, created_at LIMIT 20",
                (rs, row) -> rs.getObject(1, UUID.class));
        for (UUID id : pending) {
            try { transactions.executeWithoutResult(status -> pollOne(id)); }
            catch (RuntimeException failure) {
                log.warn("Could not receive execution diagnosis for order {}", id, failure);
                jdbc.update("UPDATE execution_outbox SET diagnosis_checked_at=CURRENT_TIMESTAMP WHERE order_id=? AND diagnosis_received_at IS NULL", id);
            }
        }
    }
    private void pollOne(UUID id) {
        var pending = jdbc.query("SELECT order_id, branch_id FROM execution_outbox WHERE order_id=? AND delivered_at IS NOT NULL AND diagnosis_received_at IS NULL FOR UPDATE SKIP LOCKED",
                (rs, row) -> new Dispatch(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)), id);
        for (var item : pending) {
            var result = client.get().uri("/api/branches/{branch}/work-orders/{order}", item.branch(), item.order()).retrieve().body(WorkOrderResponse.class);
            if (result == null || !item.order().toString().equals(result.orderId()) || !item.branch().toString().equals(result.branchId()))
                throw new IllegalStateException("Execution returned inconsistent order identity");
            if (result.diagnosis() != null && !result.diagnosis().isBlank()) {
                orders.getObject().registrarDiagnosticoExecucao(item.order(), result.diagnosis());
                jdbc.update("UPDATE execution_outbox SET diagnosis_received_at=CURRENT_TIMESTAMP WHERE order_id=?", item.order());
            } else {
                jdbc.update("UPDATE execution_outbox SET diagnosis_checked_at=CURRENT_TIMESTAMP WHERE order_id=?", item.order());
            }
        }
    }
    private record WorkOrderResponse(String orderId, String branchId, String diagnosis, String status) { }

    @Scheduled(fixedDelayString="${oficina.execution.completion-delay-ms:10000}")
    public void receiveCompletion() {
        var pending = jdbc.query("SELECT order_id FROM execution_outbox WHERE delivered_at IS NOT NULL AND diagnosis_received_at IS NOT NULL AND completion_received_at IS NULL ORDER BY completion_checked_at NULLS FIRST, created_at LIMIT 20",
                (rs, row) -> rs.getObject(1, UUID.class));
        for (UUID id : pending) {
            try { transactions.executeWithoutResult(status -> pollCompletion(id)); }
            catch (RuntimeException failure) {
                log.warn("Could not receive execution completion for order {}", id, failure);
                jdbc.update("UPDATE execution_outbox SET completion_checked_at=CURRENT_TIMESTAMP WHERE order_id=? AND completion_received_at IS NULL", id);
            }
        }
    }
    private void pollCompletion(UUID id) {
        var pending = jdbc.query("SELECT order_id, branch_id FROM execution_outbox WHERE order_id=? AND delivered_at IS NOT NULL AND diagnosis_received_at IS NOT NULL AND completion_received_at IS NULL FOR UPDATE SKIP LOCKED",
                (rs, row) -> new Dispatch(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)), id);
        for (var item : pending) {
            var result = client.get().uri("/api/branches/{branch}/work-orders/{order}", item.branch(), item.order()).retrieve().body(WorkOrderResponse.class);
            if (result == null || !item.order().toString().equals(result.orderId()) || !item.branch().toString().equals(result.branchId()))
                throw new IllegalStateException("Execution returned inconsistent order identity");
            if ("COMPLETED".equals(result.status())) {
                orders.getObject().registrarConclusaoExecucao(item.order());
                jdbc.update("UPDATE billing_saga SET status='EXECUCAO_FINALIZADA',updated_at=CURRENT_TIMESTAMP WHERE order_id=? AND branch_id=? AND status='EM_EXECUCAO'", item.order(), item.branch());
                jdbc.update("UPDATE execution_outbox SET completion_received_at=CURRENT_TIMESTAMP WHERE order_id=?", item.order());
            } else jdbc.update("UPDATE execution_outbox SET completion_checked_at=CURRENT_TIMESTAMP WHERE order_id=?", item.order());
        }
    }
    public void schedule(UUID orderId) {
        schedule(orderId, branch);
    }
    @Override public void schedule(UUID orderId, UUID branchId) {
        if (branchId == null) throw new IllegalArgumentException("Execution branch is required");
        int inserted = jdbc.update("INSERT INTO execution_outbox (order_id, branch_id) VALUES (?,?) ON CONFLICT (order_id) DO NOTHING", orderId, branchId);
        if (inserted == 0) {
            UUID existing = jdbc.queryForObject("SELECT branch_id FROM execution_outbox WHERE order_id=?", UUID.class, orderId);
            if (!branchId.equals(existing)) throw new IllegalStateException("Execution order branch conflict");
        }
    }
    @Scheduled(fixedDelayString="${oficina.execution.delay-ms:5000}")
    @Transactional
    public void deliver() {
        var pending = jdbc.query("SELECT order_id, branch_id FROM execution_outbox WHERE delivered_at IS NULL ORDER BY created_at LIMIT 20 FOR UPDATE SKIP LOCKED",
                (rs, row) -> new Dispatch(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)));
        for (var item : pending) {
            client.put().uri("/api/branches/{branch}/work-orders/{order}", item.branch(), item.order()).retrieve().toBodilessEntity();
            jdbc.update("UPDATE execution_outbox SET delivered_at=CURRENT_TIMESTAMP WHERE order_id=?", item.order());
        }
    }
    private record Dispatch(UUID order, UUID branch) { }
}
