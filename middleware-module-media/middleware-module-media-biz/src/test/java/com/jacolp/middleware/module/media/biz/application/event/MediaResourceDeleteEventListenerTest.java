package com.jacolp.middleware.module.media.biz.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.middleware.messaging.base.EventEnvelope;
import com.jacolp.middleware.messaging.tools.EventMessageCodec;
import com.jacolp.middleware.messaging.pulisher.EventRetryPublisher;
import com.jacolp.middleware.messaging.constant.EventTopology;
import com.jacolp.middleware.messaging.constant.EventTypes;
import com.jacolp.middleware.messaging.service.InboxService;
import com.jacolp.middleware.messaging.event.MediaResourceDeleteRequestedEvent;
import com.jacolp.module.media.biz.application.event.MediaResourceDeleteEventHandler;
import com.jacolp.module.media.biz.application.event.MediaResourceDeleteEventListener;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaResourceDeleteEventListenerTest {

    @Test
    void recordsTerminalFailureAfterPublishingToTheDeadLetterQueue() {
        EventMessageCodec codec = mock(EventMessageCodec.class);
        InboxService inbox = mock(InboxService.class);
        EventRetryPublisher retries = mock(EventRetryPublisher.class);
        MediaResourceDeleteEventHandler handler = mock(MediaResourceDeleteEventHandler.class);
        Message message = new Message(new byte[0]);
        MediaResourceDeleteRequestedEvent request =
                new MediaResourceDeleteRequestedEvent("23", "image/7/a.png", 9L);
        EventEnvelope envelope = new EventEnvelope("event-1", EventTypes.MEDIA_RESOURCE_DELETE_REQUESTED,
                1, "MEDIA_RESOURCE", "23", Instant.now(), null,
                new ObjectMapper().createObjectNode());
        RuntimeException failure = new IllegalStateException("storage deletion failed");
        when(codec.decode(message)).thenReturn(envelope);
        when(codec.payloadItems(envelope, MediaResourceDeleteRequestedEvent.class))
                .thenReturn(List.of(request));
        when(inbox.consume(eq(envelope), eq(MediaResourceDeleteEventHandler.CONSUMER_NAME), any()))
                .thenThrow(failure);
        when(retries.retryOrDeadLetter(EventTopology.MEDIA_DELETE_QUEUE, message, failure))
                .thenReturn(false);

        new MediaResourceDeleteEventListener(codec, inbox, retries, handler).onMessage(message);

        verify(handler).recordFailure(List.of(request), failure, true);
    }

    @Test
    void keepsTrackingQueuedWhileARetryIsPending() {
        EventMessageCodec codec = mock(EventMessageCodec.class);
        InboxService inbox = mock(InboxService.class);
        EventRetryPublisher retries = mock(EventRetryPublisher.class);
        MediaResourceDeleteEventHandler handler = mock(MediaResourceDeleteEventHandler.class);
        Message message = new Message(new byte[0]);
        MediaResourceDeleteRequestedEvent request =
                new MediaResourceDeleteRequestedEvent("23", "image/7/a.png", 9L);
        EventEnvelope envelope = new EventEnvelope("event-1", EventTypes.MEDIA_RESOURCE_DELETE_REQUESTED,
                1, "MEDIA_RESOURCE", "23", Instant.now(), null,
                new ObjectMapper().createObjectNode());
        RuntimeException failure = new IllegalStateException("storage deletion failed");
        when(codec.decode(message)).thenReturn(envelope);
        when(codec.payloadItems(envelope, MediaResourceDeleteRequestedEvent.class))
                .thenReturn(List.of(request));
        when(inbox.consume(eq(envelope), eq(MediaResourceDeleteEventHandler.CONSUMER_NAME), any()))
                .thenThrow(failure);
        when(retries.retryOrDeadLetter(EventTopology.MEDIA_DELETE_QUEUE, message, failure))
                .thenReturn(true);

        new MediaResourceDeleteEventListener(codec, inbox, retries, handler).onMessage(message);

        verify(handler).recordFailure(List.of(request), failure, false);
    }
}
