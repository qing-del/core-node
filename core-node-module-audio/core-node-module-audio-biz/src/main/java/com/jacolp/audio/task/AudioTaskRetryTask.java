package com.jacolp.audio.task;

import com.jacolp.audio.constant.AudioConstant;
import com.jacolp.audio.persistence.dataobject.AudioTaskDO;
import com.jacolp.audio.persistence.mapper.AudioTaskMapper;
import com.jacolp.audio.service.AudioTaskService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AudioTaskRetryTask {
    private static final int MAX_STREAM_LENGTH = 2000;
    private static final String RETRY_EXHAUSTED_ERROR_MESSAGE = "生成失败，请重新尝试！";
    @Autowired private AudioTaskMapper audioTaskMapper;
    @Autowired private AudioTaskService audioTaskService;
    @Autowired private StringRedisTemplate redis;
    @Value("${jacolp.audio.queue-type:redis-stream}")
    private String queueType = "redis-stream";

    /**
     * 每 5 分钟扫描一次，将卡在 PENDING 和 PROCESSING 超过 10 分钟的任务重新入队。
     */
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void retryPendingTasks() {
        LocalDateTime timeout = LocalDateTime.now().minusMinutes(AudioConstant.TASK_TIMEOUT_MINUTES);
        List<AudioTaskDO> stuckTasks = audioTaskMapper.selectPendingTimeout(timeout);
        if (stuckTasks == null || stuckTasks.isEmpty()) {
            log.debug("No stuck audio tasks found");
            return;
        }
        log.info("Found {} stuck audio tasks, processing retries...", stuckTasks.size());
        for (AudioTaskDO task : stuckTasks)
            try {
                if (audioTaskMapper.prepareRetry(task.getId(), timeout) == 1) {
                    AudioTaskDO retryTask = audioTaskMapper.selectById(task.getId());
                    if (retryTask == null) {
                        throw new IllegalStateException("Audio task disappeared after retry preparation");
                    }
                    audioTaskService.requeueTask(retryTask);
                    log.info("Re-queued stuck audio task, taskId: {}, attempt: {}",
                            retryTask.getId(), retryTask.getRetryTime());
                } else if (audioTaskMapper.markRetryExhausted(task.getId(), timeout,
                        RETRY_EXHAUSTED_ERROR_MESSAGE) == 1) {
                    log.warn("Audio task retry limit exhausted, taskId: {}", task.getId());
                }
            } catch (Exception e) {
                log.error("Failed to process retry for audio task, taskId: {}, error: {}", task.getId(), e.getMessage());
            }
        // 仅保留最近的数据（防止内存堆积）
        if ("redis-stream".equals(queueType)) {
            redis.opsForStream().trim(AudioConstant.REDIS_STREAM_KEY, MAX_STREAM_LENGTH);
        }
    }
}
