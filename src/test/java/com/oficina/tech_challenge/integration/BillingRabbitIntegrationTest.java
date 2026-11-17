package com.oficina.tech_challenge.integration;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "oficina.billing-consumer.enabled=true", "oficina.fase4.payment-gate=true", "spring.jpa.hibernate.ddl-auto=validate",
        "management.tracing.sampling.probability=0",
        "security.jwt.secret=0123456789abcdef0123456789abcdef", "external.webhook.secret=test-webhook"
})
@Testcontainers(disabledWithoutDocker = true)
class BillingRabbitIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static final GenericContainer<?> rabbit = new GenericContainer<>("rabbitmq:3.13-alpine")
            .withEnv("RABBITMQ_DEFAULT_USER", "testuser").withEnv("RABBITMQ_DEFAULT_PASS", "testpassword")
            .withExposedPorts(5672).waitingFor(org.testcontainers.containers.wait.strategy.Wait.forLogMessage(".*Server startup complete.*", 1));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbit.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "testuser");
        registry.add("spring.rabbitmq.password", () -> "testpassword");
    }
    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired JdbcTemplate jdbc;

    @Test void receivesDuplicateOnceAndSendsInvalidEventToDlq() {
        UUID eventId = UUID.randomUUID();
        String event = """
                {"eventId":"%s","eventType":"OrcamentoGerado","schemaVersion":1,
                 "occurredAt":"2026-11-05T12:00:00Z","sagaId":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                 "osId":"cccccccc-cccc-4ccc-8ccc-cccccccccccc","filialId":"dddddddd-dddd-4ddd-8ddd-dddddddddddd",
                 "correlationId":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee","payload":{"quoteId":"ffffffff-ffff-4fff-8fff-ffffffffffff","status":"PENDING"}}
                """.formatted(eventId);
        rabbitTemplate.send("oficina.billing.events", "quote.events", new Message(event.getBytes(java.nio.charset.StandardCharsets.UTF_8), new MessageProperties()));
        rabbitTemplate.send("oficina.billing.events", "quote.events", new Message(event.getBytes(java.nio.charset.StandardCharsets.UTF_8), new MessageProperties()));
        rabbitTemplate.send("oficina.billing.events", "quote.events", new Message("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8), new MessageProperties()));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertEquals(1, jdbc.queryForObject("select count(*) from billing_inbox where event_id = ?", Integer.class, eventId)));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertNotNull(rabbitTemplate.receive("oficina.os.billing.v1.dlq")));
        assertEquals(1, jdbc.queryForObject("select count(*) from billing_inbox where event_id = ?", Integer.class, eventId));
    }
}
