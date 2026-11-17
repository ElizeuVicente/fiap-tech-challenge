package com.oficina.tech_challenge.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/** Versioned contract received from the Billing bounded context. */
public record BillingEvent(UUID eventId, String eventType, UUID orderId, UUID branchId,
        UUID sagaId, UUID correlationId, JsonNode payload, String rawPayload) {
    private static final Set<String> TYPES = Set.of("OrcamentoGerado", "OrcamentoAprovado", "OrcamentoRecusado", "PagamentoConfirmado", "PagamentoRecusado", "PagamentoEstornado", "PagamentoContestado");

    public static BillingEvent parse(byte[] bytes, ObjectMapper mapper) {
        if (bytes.length > 65536) throw new AmqpRejectAndDontRequeueException("Event exceeds size limit");
        try {
            JsonNode event = mapper.readTree(bytes);
            if (event == null || !event.path("schemaVersion").isInt()
                    || event.path("schemaVersion").asInt() != 1 || !event.path("payload").isObject()) {
                throw new IllegalArgumentException("Invalid envelope");
            }
            String type = event.path("eventType").asText();
            if (!TYPES.contains(type)) throw new IllegalArgumentException("Unsupported event type");
            UUID eventId = UUID.fromString(event.path("eventId").asText());
            UUID orderId = UUID.fromString(event.path("osId").asText());
            UUID branchId = UUID.fromString(event.path("filialId").asText());
            UUID sagaId = UUID.fromString(event.path("sagaId").asText());
            UUID correlationId = UUID.fromString(event.path("correlationId").asText());
            Instant.parse(event.path("occurredAt").asText());
            return new BillingEvent(eventId, type, orderId, branchId, sagaId, correlationId,
                    event.path("payload"), new String(bytes, StandardCharsets.UTF_8));
        } catch (AmqpRejectAndDontRequeueException e) {
            throw e;
        } catch (Exception e) {
            throw new AmqpRejectAndDontRequeueException("Invalid billing event", e);
        }
    }
}
