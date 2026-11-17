package com.oficina.tech_challenge.integration;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.tech_challenge.infrastructure.messaging.BillingInbox;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class BillingInboxPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Test void storesOnceAndRejectsInvalidEvents() {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        var jdbc = new JdbcTemplate(source);
        var inbox = new BillingInbox(jdbc, new ObjectMapper());
        var json = """
                {"eventId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","eventType":"OrcamentoAprovado","schemaVersion":1,
                 "occurredAt":"2026-11-05T12:00:00Z","sagaId":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                 "osId":"cccccccc-cccc-4ccc-8ccc-cccccccccccc","filialId":"dddddddd-dddd-4ddd-8ddd-dddddddddddd",
                 "correlationId":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee","payload":{"status":"APPROVED"}}
                """.getBytes(StandardCharsets.UTF_8);
        assertTrue(inbox.receive(json));
        assertFalse(inbox.receive(json));
        assertEquals(1, jdbc.queryForObject("select count(*) from billing_inbox", Integer.class));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> inbox.receive("{}".getBytes(StandardCharsets.UTF_8)));
    }
}
