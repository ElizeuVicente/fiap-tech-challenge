package com.oficina.tech_challenge.infrastructure.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.tech_challenge.application.services.GerenciadorOrdemServico;
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

/** Dispatches authorization and start only for a payment and an already reserved snapshot. */
@Component
@ConditionalOnProperty(name="oficina.stock-saga.enabled", havingValue="true")
class ExecutionSagaCoordinator {
    private static final Logger log = LoggerFactory.getLogger(ExecutionSagaCoordinator.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final GerenciadorOrdemServico orders;
    private final RestClient execution;
    private final ObjectMapper json = new ObjectMapper();
    ExecutionSagaCoordinator(JdbcTemplate jdbc, TransactionTemplate tx, GerenciadorOrdemServico orders,
            @Value("${oficina.execution.url}") String url, @Value("${oficina.execution.service-token}") String token) {
        if (token == null || token.length() < 32) throw new IllegalArgumentException("Execution token too short");
        this.jdbc = jdbc; this.tx = tx; this.orders = orders;
        var factory = new JdkClientHttpRequestFactory(); factory.setReadTimeout(Duration.ofSeconds(5));
        execution = RestClient.builder().baseUrl(url).requestFactory(factory).defaultHeader("X-Service-Token", token).build();
    }
    @Scheduled(fixedDelayString="${oficina.stock-saga.execution-delay-ms:10000}")
    public void processPending() {
        var ids = jdbc.query("SELECT saga_id FROM billing_saga WHERE status IN ('ESTOQUE_RESERVADO','EXECUCAO_AUTORIZADA') ORDER BY updated_at LIMIT 20", (rs, row) -> rs.getObject(1, UUID.class));
        for (UUID id : ids) {
            try { tx.executeWithoutResult(status -> process(id)); }
            catch (RuntimeException failure) {
                log.warn("Execution saga step failed for {}: {}", id, failure.getClass().getSimpleName());
                jdbc.update("UPDATE billing_saga SET last_error=?,updated_at=CURRENT_TIMESTAMP WHERE saga_id=? AND status IN ('ESTOQUE_RESERVADO','EXECUCAO_AUTORIZADA')", failure.getClass().getSimpleName(), id);
            }
        }
    }
    private void process(UUID id) {
        var rows = jdbc.query("""
                SELECT s.status,s.branch_id,s.order_id,s.payment_attempt_id,r.items_snapshot
                FROM billing_saga s JOIN billing_quote_requests r ON r.saga_id=s.saga_id
                WHERE s.saga_id=? AND s.status IN ('ESTOQUE_RESERVADO','EXECUCAO_AUTORIZADA') FOR UPDATE OF s SKIP LOCKED
                """, (rs, row) -> new Saga(rs.getString(1), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5)), id);
        if (rows.isEmpty()) return;
        var saga = rows.get(0);
        if (saga.attempt() == null || saga.snapshot() == null) throw new IllegalStateException("Confirmed payment and snapshot required");
        String path = "/api/branches/{branch}/work-orders/{order}";
        if (saga.status().equals("ESTOQUE_RESERVADO")) {
            var response = execution.put().uri(path + "/authorization", saga.branch(), saga.order())
                    .body(Map.of("paymentAttemptId", saga.attempt(), "reservationId", id, "items", parts(saga.snapshot())))
                    .retrieve().body(WorkOrder.class);
            validate(response, saga, Set.of("READY", "IN_PROGRESS", "COMPLETED"));
            jdbc.update("UPDATE billing_saga SET status='EXECUCAO_AUTORIZADA',last_error=NULL,updated_at=CURRENT_TIMESTAMP WHERE saga_id=?", id);
        } else {
            var current = execution.get().uri(path, saga.branch(), saga.order()).retrieve().body(WorkOrder.class);
            validate(current, saga, Set.of("READY", "IN_PROGRESS", "COMPLETED"));
            if ("READY".equals(current.status())) {
                current = execution.post().uri(path + "/start", saga.branch(), saga.order()).retrieve().body(WorkOrder.class);
                validate(current, saga, Set.of("IN_PROGRESS", "COMPLETED"));
            }
            orders.registrarInicioExecucaoConfirmada(saga.order());
            jdbc.update("UPDATE billing_saga SET status='EM_EXECUCAO',last_error=NULL,updated_at=CURRENT_TIMESTAMP WHERE saga_id=?", id);
        }
    }
    private void validate(WorkOrder response, Saga saga, Set<String> allowed) {
        if (response == null || !saga.order().toString().equals(response.orderId()) || !saga.branch().toString().equals(response.branchId()) || !allowed.contains(response.status()))
            throw new IllegalStateException("Invalid execution response");
    }
    private List<Item> parts(String snapshot) {
        try {
            JsonNode values = json.readTree(snapshot);
            if (!values.isArray() || values.isEmpty()) throw new IllegalStateException("Invalid snapshot");
            Map<UUID, Integer> parts = new LinkedHashMap<>();
            for (JsonNode item : values) if ("PART".equals(item.path("type").asText())) {
                int qty = item.path("quantity").asInt();
                if (qty <= 0) throw new IllegalStateException("Invalid part quantity");
                parts.merge(UUID.fromString(item.path("referenceId").asText()), qty, Math::addExact);
            }
            return parts.entrySet().stream().map(item -> new Item(item.getKey(), item.getValue())).toList();
        } catch (java.io.IOException | IllegalArgumentException failure) { throw new IllegalStateException("Invalid snapshot", failure); }
    }
    private record Saga(String status, UUID branch, UUID order, UUID attempt, String snapshot) { }
    private record Item(UUID partId, int quantity) { }
    private record WorkOrder(String branchId, String orderId, String status) { }
}
