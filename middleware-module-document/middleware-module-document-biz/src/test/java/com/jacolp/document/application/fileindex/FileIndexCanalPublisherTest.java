package com.jacolp.document.application.fileindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.common.messaging.config.ReliableMessagingProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class FileIndexCanalPublisherTest {

    @Test
    void publishesDeduplicatedPersistentJsonMessagesAndConfirmsAfterSending() throws Exception {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RabbitOperations operations = mock(RabbitOperations.class);
        when(rabbitTemplate.invoke(any())).thenAnswer(invocation -> {
            RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(operations);
        });
        ReliableMessagingProperties properties = new ReliableMessagingProperties();
        properties.setConfirmTimeoutMs(1_234L);
        FileIndexCanalPublisher publisher = new FileIndexCanalPublisher(rabbitTemplate, new ObjectMapper(), properties);

        FileIndexResourceKey note = new FileIndexResourceKey(FileIndexResourceType.NOTE, 7L);
        publisher.publish(List.of(note, note, new FileIndexResourceKey(FileIndexResourceType.IMAGE, 8L)));

        ArgumentCaptor<Message> messages = ArgumentCaptor.forClass(Message.class);
        verify(operations, org.mockito.Mockito.times(2)).send(eq(FileIndexCanalTopology.EXCHANGE),
                eq(FileIndexCanalTopology.ROUTING_KEY), messages.capture());
        verify(operations).waitForConfirmsOrDie(1_234L);
        List<FileIndexRefreshMessage> events = messages.getAllValues().stream()
                .map(message -> read(message)).toList();
        assertThat(events).extracting(FileIndexRefreshMessage::resourceType, FileIndexRefreshMessage::resourceId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("NOTE", "7"),
                        org.assertj.core.groups.Tuple.tuple("IMAGE", "8"));
        assertThat(messages.getAllValues()).allSatisfy(message -> assertThat(message.getMessageProperties()
                .getMessageId()).isNotBlank());
        InOrder order = inOrder(operations);
        order.verify(operations, org.mockito.Mockito.times(2)).send(eq(FileIndexCanalTopology.EXCHANGE),
                eq(FileIndexCanalTopology.ROUTING_KEY), any(Message.class));
        order.verify(operations).waitForConfirmsOrDie(1_234L);
    }

    private static FileIndexRefreshMessage read(Message message) {
        try {
            return new ObjectMapper().readValue(message.getBody(), FileIndexRefreshMessage.class);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
