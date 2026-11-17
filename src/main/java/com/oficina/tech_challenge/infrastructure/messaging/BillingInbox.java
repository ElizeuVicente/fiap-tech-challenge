package com.oficina.tech_challenge.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records delivery only; business transitions require a separate Saga implementation. */
@Service
public class BillingInbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public BillingInbox(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    @Transactional
    public boolean receive(byte[] bytes) {
        return receive(BillingEvent.parse(bytes, mapper));
    }

    @Transactional
    public boolean receive(BillingEvent event) {
        int count = jdbc.update("INSERT INTO billing_inbox (event_id,event_type,order_id,branch_id,saga_id,correlation_id,payload) VALUES (?,?,?,?,?,?,?) ON CONFLICT (event_id) DO NOTHING",
                event.eventId(), event.eventType(), event.orderId(), event.branchId(), event.sagaId(), event.correlationId(), event.rawPayload());
        return count == 1;
    }
}
