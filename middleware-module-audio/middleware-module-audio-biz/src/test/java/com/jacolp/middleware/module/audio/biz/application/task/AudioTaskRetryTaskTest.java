package com.jacolp.middleware.module.audio.biz.application.task;

import com.jacolp.audio.biz.constant.AudioConstant;
import com.jacolp.audio.biz.service.AudioTaskService;
import com.jacolp.audio.biz.task.AudioTaskRetryTask;
import com.jacolp.audio.biz.persistence.dataobject.AudioTaskDO;
import com.jacolp.audio.biz.persistence.mapper.AudioTaskMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioTaskRetryTaskTest {
    @Test
    @SuppressWarnings("unchecked")
    void requeuesTimedOutTasksAndTrimsStreamToExistingLimit() {
        AudioTaskMapper mapper = mock(AudioTaskMapper.class);
        AudioTaskService service = mock(AudioTaskService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations stream = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(stream);
        AudioTaskDO task = new AudioTaskDO();
        task.setId(30L);
        task.setStatus(1);
        task.setRetryTime(0);
        AudioTaskDO preparedTask = new AudioTaskDO();
        preparedTask.setId(30L);
        preparedTask.setStatus(0);
        preparedTask.setRetryTime(1);
        when(mapper.selectPendingTimeout(any())).thenReturn(List.of(task));
        when(mapper.prepareRetry(any(), any())).thenReturn(1);
        when(mapper.selectById(30L)).thenReturn(preparedTask);
        AudioTaskRetryTask retryTask = new AudioTaskRetryTask();
        ReflectionTestUtils.setField(retryTask, "audioTaskMapper", mapper);
        ReflectionTestUtils.setField(retryTask, "audioTaskService", service);
        ReflectionTestUtils.setField(retryTask, "redis", redis);

        retryTask.retryPendingTasks();

        verify(mapper).prepareRetry(org.mockito.ArgumentMatchers.eq(30L), any());
        verify(service).requeueTask(preparedTask);
        verify(stream).trim(AudioConstant.REDIS_STREAM_KEY, 2000L);
    }
}
