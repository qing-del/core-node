package com.jacolp.audio.biz.task;

import com.jacolp.audio.biz.constant.AudioConstant;
import com.jacolp.audio.biz.service.AudioTaskService;
import com.jacolp.audio.biz.persistence.dataobject.AudioTaskDO;
import com.jacolp.audio.biz.persistence.mapper.AudioTaskMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @Slf4j
public class AudioTaskRetryTask {
    private static final int MAX_STREAM_LENGTH = 2000;
    @Autowired private AudioTaskMapper audioTaskMapper;
    @Autowired private AudioTaskService audioTaskService;
    @Autowired private StringRedisTemplate redis;

    /** 每 5 分钟扫描一次，将卡在 PENDING 超过 10 分钟的任务重新入队。 */
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void retryPendingTasks() {
        LocalDateTime timeout = LocalDateTime.now().minusMinutes(AudioConstant.TASK_TIMEOUT_MINUTES);
        List<AudioTaskDO> stuckTasks = audioTaskMapper.selectPendingTimeout(timeout);
        if (stuckTasks == null || stuckTasks.isEmpty()) { log.debug("No stuck audio tasks found"); return; }
        log.info("Found {} stuck audio tasks, re-queuing...", stuckTasks.size());
        for (AudioTaskDO task : stuckTasks) try { audioTaskService.requeueTask(task); log.info("Re-queued stuck audio task, taskId: {}", task.getId()); }
        catch (Exception e) { log.error("Failed to re-queue audio task, taskId: {}, error: {}", task.getId(), e.getMessage()); }
        // 仅保留最近的数据（防止内存堆积）
        redis.opsForStream().trim(AudioConstant.REDIS_STREAM_KEY, MAX_STREAM_LENGTH);
    }
}
