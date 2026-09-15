package com.jacolp.middleware.module.audio.biz.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.audio.constant.AudioConstant;
import com.jacolp.audio.persistence.dataobject.AudioTaskDO;
import com.jacolp.audio.service.RabbitMqTaskPublisher;
import com.jacolp.audio.service.RedisStreamTaskPublisher;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class AudioTaskPublisherAttemptTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void redisStreamPayloadDefaultsNewTaskToAttemptZero() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations stream = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(stream);
        RedisStreamTaskPublisher publisher = new RedisStreamTaskPublisher(redis);

        publisher.publish(task(null));

        ArgumentCaptor<Map<String, String>> payload = ArgumentCaptor.forClass(Map.class);
        verify(stream).add(eq(AudioConstant.REDIS_STREAM_KEY), payload.capture());
        assertThat(payload.getValue()).containsEntry("attempt", "0");
    }

    @Test
    void rabbitMqPayloadCarriesPreparedRetryAttempt() throws Exception {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RabbitMqTaskPublisher publisher = new RabbitMqTaskPublisher(rabbit, objectMapper);

        publisher.publish(task(2));

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq(RabbitMqTaskPublisher.EXCHANGE),
                eq(RabbitMqTaskPublisher.ROUTING_KEY), message.capture());
        JsonNode payload = objectMapper.readTree(message.getValue().getBody());
        assertThat(payload.get("attempt").asText()).isEqualTo("2");
    }

    private static AudioTaskDO task(Integer attempt) {
        AudioTaskDO task = new AudioTaskDO();
        task.setId(50L);
        task.setUserId(9L);
        task.setRetryTime(attempt);
        task.setSpeed(BigDecimal.ONE);
        task.setNoiseType(AudioConstant.NOISE_TYPE_PURE);
        task.setNoiseFactor(BigDecimal.ZERO);
        task.setSourceText("lease test");
        return task;
    }
}
