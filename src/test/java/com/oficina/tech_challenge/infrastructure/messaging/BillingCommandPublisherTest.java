package com.oficina.tech_challenge.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class BillingCommandPublisherTest {
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void pending(JdbcTemplate jdbc, UUID id) throws Exception {
        var rs = mock(ResultSet.class);
        when(rs.getString(1)).thenReturn(id.toString());
        when(rs.getString(2)).thenReturn("{\"event\":\"cancel\"}");
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });
    }

    @Test void confirmationMarksCommandPublished() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var rabbit = mock(RabbitTemplate.class);
        var id = UUID.randomUUID();
        pending(jdbc, id);
        doAnswer(call -> {
            Message message = call.getArgument(2);
            assertEquals(id.toString(), message.getMessageProperties().getMessageId());
            assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getDeliveryMode());
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(eq("oficina.billing.commands"), eq("quote.commands"), any(Message.class), any(CorrelationData.class));
        new BillingCommandPublisher(jdbc, rabbit).publishPending();
        verify(jdbc).update(contains("published_at=CURRENT_TIMESTAMP"), eq(id));
    }

    @Test void brokerRejectionKeepsCommandPending() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var rabbit = mock(RabbitTemplate.class);
        pending(jdbc, UUID.randomUUID());
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "rejected"));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThrows(IllegalStateException.class, () -> new BillingCommandPublisher(jdbc, rabbit).publishPending());
        verify(jdbc, never()).update(anyString(), any(Object.class));
    }
}
