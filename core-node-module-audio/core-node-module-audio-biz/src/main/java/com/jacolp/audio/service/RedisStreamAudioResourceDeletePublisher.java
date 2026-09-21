package com.jacolp.audio.service;

import com.jacolp.audio.persistence.dataobject.AudioTaskDO;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "jacolp.audio", name = "queue-type", havingValue = "redis-stream", matchIfMissing = true)
public class RedisStreamAudioResourceDeletePublisher implements AudioResourceDeletePublisher {
    public static final String STREAM_KEY = "stream:audio:deletions";
    public static final String STREAM_GROUP = "audio-delete-consumer-group";

    private final StringRedisTemplate redis;

    public RedisStreamAudioResourceDeletePublisher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @PostConstruct
    public void initStreamGroup() {
        try {
            redis.opsForStream().createGroup(STREAM_KEY, STREAM_GROUP);
            log.info("Redis Stream group '{}' created", STREAM_GROUP);
        } catch (DataAccessException e) {
            log.debug("Redis Stream group already exists: {}", e.getMessage());
        }
    }

    @Override
    public void publish(AudioTaskDO task) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("taskId", String.valueOf(task.getId()));
        payload.put("userId", String.valueOf(task.getUserId()));
        payload.put("resultUrl", task.getResultUrl() == null ? "" : task.getResultUrl());
        payload.put("audioSize", task.getAudioSize() == null ? "0" : String.valueOf(task.getAudioSize()));
        redis.opsForStream().add(STREAM_KEY, payload);
        log.debug("Audio resource deletion pushed to Redis Stream, taskId: {}", task.getId());
    }
}
