package com.jacolp.audio.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.audio.persistence.dataobject.AudioTaskDO;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "jacolp.audio", name = "queue-type", havingValue = "rabbitmq")
public class RabbitMqAudioResourceDeletePublisher implements AudioResourceDeletePublisher {
    public static final String EXCHANGE = "audio.delete.exchange";
    public static final String ROUTING_KEY = "audio.delete";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public RabbitMqAudioResourceDeletePublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(AudioTaskDO task) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("taskId", String.valueOf(task.getId()));
        payload.put("userId", String.valueOf(task.getUserId()));
        payload.put("resultUrl", task.getResultUrl() == null ? "" : task.getResultUrl());
        payload.put("audioSize", task.getAudioSize() == null ? "0" : String.valueOf(task.getAudioSize()));

        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        rabbitTemplate.send(EXCHANGE, ROUTING_KEY, new Message(toJson(payload), properties));
        log.debug("Audio resource deletion pushed to RabbitMQ, taskId: {}", task.getId());
    }

    private byte[] toJson(Map<String, String> payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audio resource deletion message", e);
        }
    }
}
