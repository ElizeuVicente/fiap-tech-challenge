package com.oficina.tech_challenge.infrastructure.messaging;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.context.annotation.Configuration
@EnableScheduling
class BillingCommandSchedulingConfiguration { }

/** Delivers persisted Saga compensation commands at least once. */
@Component
@ConditionalOnProperty(name = "oficina.billing-command-publisher.enabled", havingValue = "true")
class BillingCommandPublisher {
    private static final String EXCHANGE = "oficina.billing.commands";
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;

    BillingCommandPublisher(JdbcTemplate jdbc, RabbitTemplate rabbit) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.rabbit.setMandatory(true);
    }

    @Scheduled(fixedDelayString = "${oficina.billing-command-publisher.delay-ms:5000}")
    @Transactional
    public void publishPending() {
        List<Command> commands = jdbc.query("""
                SELECT command_id, payload FROM billing_command_outbox
                WHERE published_at IS NULL ORDER BY created_at LIMIT 25 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new Command(UUID.fromString(rs.getString(1)), rs.getString(2)));
        for (Command command : commands) {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setMessageId(command.id().toString());
            CorrelationData correlation = new CorrelationData(command.id().toString());
            rabbit.send(EXCHANGE, "quote.commands", new Message(command.payload().getBytes(StandardCharsets.UTF_8), properties), correlation);
            try {
                var confirm = correlation.getFuture().get();
                if (!confirm.isAck() || correlation.getReturned() != null) throw new IllegalStateException("Broker rejected compensation command");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted publishing compensation command", e);
            } catch (Exception e) {
                throw new IllegalStateException("Compensation command was not confirmed", e);
            }
            jdbc.update("UPDATE billing_command_outbox SET published_at=CURRENT_TIMESTAMP WHERE command_id=?", command.id());
        }
    }
    private record Command(UUID id, String payload) { }
}
