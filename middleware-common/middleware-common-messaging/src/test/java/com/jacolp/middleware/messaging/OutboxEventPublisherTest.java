package com.jacolp.middleware.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;

import com.jacolp.common.messaging.base.EventEnvelope;
import com.jacolp.common.messaging.base.OutboxRepository;
import com.jacolp.common.messaging.config.ReliableMessagingProperties;
import com.jacolp.common.messaging.constant.EventTypes;
import com.jacolp.common.messaging.pulisher.OutboxEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OutboxEventPublisherTest {
    private OutboxRepository repository;
    private ReliableMessagingProperties properties;
    private OutboxEventPublisher publisher;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxRepository.class);
        properties = new ReliableMessagingProperties();
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        publisher = new OutboxEventPublisher(repository, objectMapper, properties);
    }

    @Test
    void writesStableEnvelopeToOutboxWithoutCallingRabbitMq() {
        EventEnvelope event = publisher.publish(EventTypes.AUDIT_REVIEWED, EventTypes.AUDIT_REVIEWED,
                "AUDIT", 42L, "request-1", new SamplePayload("APPROVED"));

        assertThat(event.eventType()).isEqualTo(EventTypes.AUDIT_REVIEWED);
        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.aggregateId()).isEqualTo("42");
        assertThat(event.payload().get("decision").asText()).isEqualTo("APPROVED");
        verify(repository).insert(eq(event), eq(EventTypes.AUDIT_REVIEWED), any(String.class));
    }

    @Test
    void partitionsLargeLogicalBatchesUsingConfiguredShardSize() {
        properties.setShardSize(2);
        List<EventEnvelope> events = publisher.publishPartitioned("sample", "sample", "SAMPLE", 1,
                null, List.of(1, 2, 3, 4, 5));

        assertThat(events).hasSize(3);
        verify(repository, times(3)).insert(any(), eq("sample"), any(String.class));
    }

    @Test
    void rejectsOversizedPayloadBeforeDatabaseInsert() {
        properties.setMaxPayloadBytes(32);
        assertThatThrownBy(() -> publisher.publish("sample", "sample", "SAMPLE", 1, null,
                new SamplePayload("x".repeat(128))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum size");
    }

    private record SamplePayload(String decision) {
    }
}
