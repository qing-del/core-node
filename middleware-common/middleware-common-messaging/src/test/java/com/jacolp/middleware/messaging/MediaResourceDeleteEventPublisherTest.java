package com.jacolp.middleware.messaging;

import java.util.List;

import com.jacolp.middleware.messaging.constant.EventTypes;
import com.jacolp.middleware.messaging.event.MediaResourceDeleteRequestedEvent;
import com.jacolp.middleware.messaging.pulisher.MediaResourceDeleteEventPublisher;
import com.jacolp.middleware.messaging.pulisher.OutboxEventPublisher;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MediaResourceDeleteEventPublisherTest {

    @Test
    void writesDeleteRequestsToTheDedicatedRoutingKey() {
        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        MediaResourceDeleteEventPublisher publisher = new MediaResourceDeleteEventPublisher(outbox);
        List<MediaResourceDeleteRequestedEvent> events = List.of(
                new MediaResourceDeleteRequestedEvent("23", "image/7/a.png", 9L));

        publisher.publish(events);

        verify(outbox).publishPartitioned(eq(EventTypes.MEDIA_RESOURCE_DELETE_REQUESTED),
                eq(EventTypes.MEDIA_RESOURCE_DELETE_REQUESTED), eq("MEDIA_RESOURCE"), eq("23"),
                anyString(), eq(events));
    }
}
