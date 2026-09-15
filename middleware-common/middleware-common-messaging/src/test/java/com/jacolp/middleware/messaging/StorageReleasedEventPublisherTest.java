package com.jacolp.middleware.messaging;

import java.util.List;

import com.jacolp.common.messaging.constant.EventTypes;
import com.jacolp.common.messaging.event.StorageReleasedEvent;
import com.jacolp.common.messaging.pulisher.OutboxEventPublisher;
import com.jacolp.common.messaging.pulisher.StorageReleasedEventPublisher;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StorageReleasedEventPublisherTest {

    @Test
    void writesStorageFactsToTheSystemRoutingKey() {
        OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        StorageReleasedEventPublisher publisher = new StorageReleasedEventPublisher(outbox);
        List<StorageReleasedEvent> events = List.of(new StorageReleasedEvent(7L, "NOTE", "19", 128L));

        publisher.publish(events);

        verify(outbox).publishPartitioned(eq(EventTypes.STORAGE_RELEASED), eq(EventTypes.STORAGE_RELEASED),
                eq("STORAGE_RESOURCE"), eq("19"), anyString(), eq(events));
    }
}
