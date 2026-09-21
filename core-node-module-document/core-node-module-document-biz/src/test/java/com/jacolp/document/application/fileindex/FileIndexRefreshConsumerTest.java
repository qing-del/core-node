package com.jacolp.document.application.fileindex;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.common.messaging.pulisher.EventRetryPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;

class FileIndexRefreshConsumerTest {

    @Test
    void rebuildsCurrentResourceFromOnlyTheLocationMessage() throws Exception {
        FileIndexProjectionService projectionService = mock(FileIndexProjectionService.class);
        FileIndexRefreshConsumer consumer = new FileIndexRefreshConsumer(new ObjectMapper(), projectionService,
                mock(EventRetryPublisher.class));
        FileIndexRefreshMessage event = FileIndexRefreshMessage.forResource(
                new FileIndexResourceKey(FileIndexResourceType.DOCUMENT, 9L));

        consumer.onMessage(new Message(new ObjectMapper().writeValueAsBytes(event)));

        verify(projectionService).refresh(new FileIndexResourceKey(FileIndexResourceType.DOCUMENT, 9L));
    }

    @Test
    void sendsProjectionFailuresThroughExistingRetryQueue() throws Exception {
        FileIndexProjectionService projectionService = mock(FileIndexProjectionService.class);
        EventRetryPublisher retryPublisher = mock(EventRetryPublisher.class);
        doThrow(new IllegalStateException("ES unavailable")).when(projectionService).refresh(any());
        FileIndexRefreshConsumer consumer = new FileIndexRefreshConsumer(new ObjectMapper(), projectionService,
                retryPublisher);
        FileIndexRefreshMessage event = FileIndexRefreshMessage.forResource(
                new FileIndexResourceKey(FileIndexResourceType.NOTE, 7L));
        Message message = new Message(new ObjectMapper().writeValueAsBytes(event));

        consumer.onMessage(message);

        verify(retryPublisher).retryOrDeadLetter(eq(FileIndexCanalTopology.QUEUE), eq(message), any());
    }
}
