package com.oficina.tech_challenge.infrastructure.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.tech_challenge.infrastructure.saga.BillingSagaCoordinator;

@Configuration
@ConditionalOnProperty(name = "oficina.billing-consumer.enabled", havingValue = "true")
class BillingMessagingConfiguration {
    static final String QUEUE = "oficina.os.billing.v1";
    @Bean DirectExchange billingEventsExchange() { return new DirectExchange("oficina.billing.events", true, false); }
    @Bean DirectExchange billingDeadLetterExchange() { return new DirectExchange("oficina.os.billing.dlx", true, false); }
    @Bean Queue billingInboxQueue() { return QueueBuilder.durable(QUEUE).deadLetterExchange("oficina.os.billing.dlx").deadLetterRoutingKey("rejected").build(); }
    @Bean Queue billingDeadLetterQueue() { return QueueBuilder.durable("oficina.os.billing.v1.dlq").build(); }
    @Bean Binding billingEventsBinding(@Qualifier("billingInboxQueue") Queue queue, @Qualifier("billingEventsExchange") DirectExchange exchange) { return BindingBuilder.bind(queue).to(exchange).with("quote.events"); }
    @Bean Binding billingDeadLetterBinding(@Qualifier("billingDeadLetterQueue") Queue queue, @Qualifier("billingDeadLetterExchange") DirectExchange exchange) { return BindingBuilder.bind(queue).to(exchange).with("rejected"); }
}

@Component
@ConditionalOnProperty(name = "oficina.billing-consumer.enabled", havingValue = "true")
class BillingListener {
    private final BillingSagaCoordinator coordinator;
    private final ObjectMapper mapper;
    BillingListener(BillingSagaCoordinator coordinator, ObjectMapper mapper) { this.coordinator = coordinator; this.mapper = mapper; }
    @RabbitListener(queues = BillingMessagingConfiguration.QUEUE)
    public void receive(Message message) { coordinator.receive(BillingEvent.parse(message.getBody(), mapper)); }
}
