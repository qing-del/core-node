package com.jacolp.common.messaging.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.jacolp.common.messaging.base.EventEnvelope;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

/**
 * 事件编解码器：负责 RabbitMQ 消息 {@link Message} 与 {@link EventEnvelope} 之间的 JSON 转换，
 * 并提供从 envelope 中取出单个 payload（{@link #payload}）或分片批量 payload
 * （{@link #payloadItems}，兼容标准 {items:[...]} 分片格式）的能力。
 */
@Component
public class EventMessageCodec {
    private final ObjectMapper objectMapper;

    public EventMessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EventEnvelope decode(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), EventEnvelope.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid domain event envelope", e);
        }
    }

    public <T> T payload(EventEnvelope envelope, Class<T> type) {
        return convert(envelope.payload(), type);
    }

    public <T> T convert(JsonNode value, Class<T> type) {
        try {
            return objectMapper.treeToValue(value, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid domain event payload", e);
        }
    }

    /** Supports both a singular payload and the standard partitioned {items:[...]} payload. */
    public <T> List<T> payloadItems(EventEnvelope envelope, Class<T> type) {
        JsonNode items = envelope.payload().get("items");
        if (items == null) return List.of(convert(envelope.payload(), type));
        if (!items.isArray()) throw new IllegalArgumentException("Partitioned event items must be an array");
        List<T> values = new ArrayList<>();
        items.forEach(item -> values.add(convert(item, type)));
        return List.copyOf(values);
    }
}
