package com.jacolp.middleware.module.audio.biz.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.audio.persistence.dataobject.AudioTaskDO;
import com.jacolp.audio.service.RabbitMqAudioResourceDeletePublisher;
import com.jacolp.audio.service.RedisStreamAudioResourceDeletePublisher;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioResourceDeletePublisherTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void redisPublisherUsesDedicatedStreamAndCleanupPayload() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations stream = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(stream);
        RedisStreamAudioResourceDeletePublisher publisher =
                new RedisStreamAudioResourceDeletePublisher(redis);

        publisher.publish(task());

        ArgumentCaptor<Map<String, String>> payload = ArgumentCaptor.forClass(Map.class);
        verify(stream).add(eq(RedisStreamAudioResourceDeletePublisher.STREAM_KEY), payload.capture());
        assertThat(payload.getValue()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "taskId", "50",
                "userId", "9",
                "resultUrl", "https://audio.example/50.mp3",
                "audioSize", "512"));
    }

    @Test
    void rabbitPublisherUsesDedicatedRouteAndPersistentJsonMessage() throws Exception {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RabbitMqAudioResourceDeletePublisher publisher =
                new RabbitMqAudioResourceDeletePublisher(rabbitTemplate, objectMapper);

        publisher.publish(task());

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq(RabbitMqAudioResourceDeletePublisher.EXCHANGE),
                eq(RabbitMqAudioResourceDeletePublisher.ROUTING_KEY),
                message.capture());
        JsonNode json = objectMapper.readTree(new String(message.getValue().getBody(), StandardCharsets.UTF_8));
        assertThat(json.get("taskId").asText()).isEqualTo("50");
        assertThat(json.get("userId").asText()).isEqualTo("9");
        assertThat(json.get("resultUrl").asText()).isEqualTo("https://audio.example/50.mp3");
        assertThat(json.get("audioSize").asLong()).isEqualTo(512L);
        assertThat(message.getValue().getMessageProperties().getDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
    }

    private static AudioTaskDO task() {
        AudioTaskDO task = new AudioTaskDO();
        task.setId(50L);
        task.setUserId(9L);
        task.setResultUrl("https://audio.example/50.mp3");
        task.setAudioSize(512L);
        return task;
    }
}
