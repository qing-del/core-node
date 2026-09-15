package com.jacolp.audio.service;

import com.jacolp.audio.constant.AudioConstant;
import com.jacolp.audio.persistence.dataobject.AudioTaskDO;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "jacolp.audio", name = "queue-type", havingValue = "redis-stream", matchIfMissing = true)
public class RedisStreamTaskPublisher implements AudioTaskPublisher {
    private final StringRedisTemplate redis;

    public RedisStreamTaskPublisher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @PostConstruct
    public void initStreamGroup() {
        try {
            redis.opsForStream().createGroup(AudioConstant.REDIS_STREAM_KEY, AudioConstant.STREAM_GROUP);
            log.info("Redis Stream group '{}' created", AudioConstant.STREAM_GROUP);
        } catch (DataAccessException e) {
            log.debug("Redis Stream group already exists: {}", e.getMessage());
        }
    }

    @Override
    public void publish(AudioTaskDO task) {
        Map<String, String> payload = new HashMap<>();
        payload.put("taskId", String.valueOf(task.getId()));
        payload.put("attempt", String.valueOf(task.getRetryTime() == null ? 0 : task.getRetryTime()));
        payload.put("userId", String.valueOf(task.getUserId()));
        payload.put("speed", task.getSpeed().toPlainString());
        payload.put("noiseType", task.getNoiseType());
        payload.put("noiseFactor", task.getNoiseFactor().toPlainString());
        payload.put("text", task.getSourceText());
        redis.opsForStream().add(AudioConstant.REDIS_STREAM_KEY, payload);
        log.debug("Audio task pushed to Redis Stream, taskId: {}, attempt: {}",
                task.getId(), payload.get("attempt"));
    }
}
