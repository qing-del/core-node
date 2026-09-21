package com.jacolp.document.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.common.messaging.config.ReliableMessagingProperties;
import com.jacolp.document.api.model.DocumentScheduleType;
import com.jacolp.document.config.DocumentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.mockito.ArgumentCaptor;

class DocumentSchedulePublisherTest {

    @Test
    void serializesOnlyTheSmallSchedulingSignalAsPersistentJson() throws Exception {
        DocumentSchedulePublisher publisher = new DocumentSchedulePublisher(mock(RabbitTemplate.class),
                new ObjectMapper(), new DocumentProperties(), new ReliableMessagingProperties());

        Message message = publisher.newScheduleMessage(17L, DocumentScheduleType.FLUSH_LOG, 1_234L, null);

        DocumentScheduleMessage schedule = new ObjectMapper().readValue(message.getBody(), DocumentScheduleMessage.class);
        assertThat(schedule.documentId()).isEqualTo(17L);
        assertThat(schedule.type()).isEqualTo(DocumentScheduleType.FLUSH_LOG);
        assertThat(schedule.triggerTime()).isEqualTo(1_234L);
        assertThat(schedule.closeToken()).isNull();
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
    }

    @Test
    void sendsFlushSignalToDelayQueueThenWaitsForBrokerConfirm() throws Exception {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RabbitOperations operations = mock(RabbitOperations.class);
        when(rabbitTemplate.invoke(any())).thenAnswer(invocation -> {
            RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(operations);
        });
        DocumentProperties documentProperties = new DocumentProperties();
        documentProperties.getFlushLog().setDelayMs(2_000L);
        ReliableMessagingProperties messagingProperties = new ReliableMessagingProperties();
        messagingProperties.setConfirmTimeoutMs(1_234L);
        ObjectMapper objectMapper = new ObjectMapper();
        DocumentSchedulePublisher publisher = new DocumentSchedulePublisher(rabbitTemplate, objectMapper,
                documentProperties, messagingProperties);

        long before = System.currentTimeMillis();
        publisher.scheduleFlushLog(17L);
        long after = System.currentTimeMillis();

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(operations).send(eq(""), eq(DocumentScheduleTopology.FLUSH_LOG_DELAY_QUEUE), message.capture());
        verify(operations).waitForConfirmsOrDie(1_234L);
        DocumentScheduleMessage schedule = objectMapper.readValue(message.getValue().getBody(), DocumentScheduleMessage.class);
        assertThat(schedule.documentId()).isEqualTo(17L);
        assertThat(schedule.type()).isEqualTo(DocumentScheduleType.FLUSH_LOG);
        assertThat(schedule.triggerTime()).isBetween(before + 2_000L, after + 2_000L);
    }
}
