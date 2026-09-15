package com.jacolp.middleware.messaging.pulisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.jacolp.middleware.messaging.base.EventEnvelope;
import com.jacolp.middleware.messaging.base.OutboxRepository;
import com.jacolp.middleware.messaging.config.ReliableMessagingProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes events to the database transaction only; this class never calls RabbitMQ. */
@Service
public class OutboxEventPublisher {
    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final ReliableMessagingProperties properties;

    public OutboxEventPublisher(OutboxRepository repository, ObjectMapper objectMapper,
                                ReliableMessagingProperties properties) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** Publishes a single event to the outbox table.
     *
     * @param eventType The type of the event, e.g. "USER_PROFILE_CHANGED".
     * @param routingKey The routing key to use when publishing the event to RabbitMQ.
     * @param aggregateType The type of the aggregate that the event is related to, e.g. "USER".
     * @param aggregateId The ID of the aggregate that the event is related to.
     * @param correlationId The correlation ID to use for tracing the event through the system.
     * @param payload The payload of the event, which will be serialized to JSON.
     * @return The EventEnvelope that was published to the outbox table.
     * */
    @Transactional(propagation = Propagation.MANDATORY)
    public EventEnvelope publish(String eventType, String routingKey, String aggregateType,
                                 Object aggregateId, String correlationId, Object payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString(),   // eventId 采用 UUID 生成 保证唯一性
                eventType,
                1,
                aggregateType,
                String.valueOf(aggregateId),
                Instant.now(),
                correlationId,
                objectMapper.valueToTree(payload)   // payload 转换为 JsonNode
        );
        String serialized = serialize(envelope);    // 序列化 envelope

        // 检查序列化之后是否超过最大限制
        if (serialized.getBytes(StandardCharsets.UTF_8).length > properties.getMaxPayloadBytes()) {
            throw new IllegalArgumentException("event payload exceeds configured maximum size");
        }
        repository.insert(envelope, routingKey, serialized);    // 插入到 DB
        return envelope;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> List<EventEnvelope> publishPartitioned(String eventType, String routingKey,
            String aggregateType, Object aggregateId, String correlationId, List<T> items) {
        Objects.requireNonNull(items, "items must not be null");
        // 获取分片大小，确保至少为 1
        int shardSize = Math.max(1, properties.getShardSize());
        List<EventEnvelope> events = new ArrayList<>();

        // 将 items 分片，每个分片大小为 shardSize，创建 EventShard 并发布
        for (int start = 0; start < items.size(); start += shardSize) {
            int end = Math.min(items.size(), start + shardSize);    // 计算结束索引
            events.add(
                    publish(
                            eventType,
                            routingKey,
                            aggregateType,
                            aggregateId,
                            correlationId,
                            new EventShard<>(start / shardSize,
                                    (items.size() + shardSize - 1) / shardSize,
                                    List.copyOf(items.subList(start, end)))
                    ));
        }
        return List.copyOf(events);
    }

    /**
     * Serializes an EventEnvelope to a JSON string.
     *
     * @param envelope The EventEnvelope to serialize.
     * @return The serialized EventEnvelope as a JSON string.
     */
    private String serialize(EventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize event " + envelope.eventType(), e);
        }
    }

    public record EventShard<T>(int shardIndex, int shardCount, List<T> items) {
    }
}
