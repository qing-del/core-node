package com.jacolp.middleware.module.audio.biz.application.service;

import com.jacolp.audio.biz.service.AudioTaskServiceImpl;
import com.jacolp.context.BaseContext;
import com.jacolp.exception.RateLimitExceededException;
import com.jacolp.audio.biz.constant.AudioConstant;
import com.jacolp.audio.biz.domain.dto.AudioCallbackFinishDTO;
import com.jacolp.audio.biz.domain.dto.AudioTaskSubmitDTO;
import com.jacolp.audio.biz.persistence.dataobject.AudioTaskDO;
import com.jacolp.audio.biz.persistence.mapper.AudioTaskMapper;
import com.jacolp.module.system.api.quota.ConsumeQuotaCommand;
import com.jacolp.module.system.api.quota.ConsumeQuotaResult;
import com.jacolp.module.system.api.quota.QuotaSnapshot;
import com.jacolp.module.system.api.quota.QuotaType;
import com.jacolp.module.system.api.quota.UserQuotaApi;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioTaskServiceImplTest {
    private AudioTaskMapper mapper;
    private UserQuotaApi quotaApi;
    private StringRedisTemplate redis;
    private StreamOperations stream;
    private AudioTaskServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = mock(AudioTaskMapper.class);
        quotaApi = mock(UserQuotaApi.class);
        redis = mock(StringRedisTemplate.class);
        stream = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(stream);
        service = new AudioTaskServiceImpl();
        ReflectionTestUtils.setField(service, "audioTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "userQuotaApi", quotaApi);
        ReflectionTestUtils.setField(service, "redis", redis);
        BaseContext.setCurrentId(9L);
    }

    @AfterEach
    void clearContext() { BaseContext.remove(); }

    @Test
    void submitConsumesQuotaInsertsPendingTaskAndPublishesSixFieldPayload() {
        when(quotaApi.consume(any())).thenReturn(quotaResult(true));
        org.mockito.Mockito.doAnswer(invocation -> { invocation.getArgument(0, AudioTaskDO.class).setId(42L); return 1; }).when(mapper).insert(any());

        assertThat(service.submitTask(submitDto()).getTaskId()).isEqualTo(42L);

        ArgumentCaptor<ConsumeQuotaCommand> quota = ArgumentCaptor.forClass(ConsumeQuotaCommand.class);
        verify(quotaApi).consume(quota.capture());
        assertThat(quota.getValue().userId()).isEqualTo(9L);
        assertThat(quota.getValue().quotaType()).isEqualTo(QuotaType.DAILY_API_CALL);
        assertThat(quota.getValue().amount()).isEqualTo(1L);
        ArgumentCaptor<AudioTaskDO> task = ArgumentCaptor.forClass(AudioTaskDO.class);
        verify(mapper).insert(task.capture());
        assertThat(task.getValue().getStatus()).isEqualTo(AudioConstant.TASK_STATUS_PENDING);
        assertThat(task.getValue().getNoiseFactor()).isEqualByComparingTo("0.5");
        ArgumentCaptor<Map<String, String>> payload = ArgumentCaptor.forClass(Map.class);
        verify(stream).add(eq(AudioConstant.REDIS_STREAM_KEY), payload.capture());
        assertThat(payload.getValue()).containsExactlyInAnyOrderEntriesOf(Map.of("taskId", "42", "userId", "9", "speed", "1.2", "noiseType", "PURE", "noiseFactor", "0.5", "text", "hello"));
    }

    @Test
    void rejectedQuotaDoesNotWriteDatabaseOrStream() {
        when(quotaApi.consume(any())).thenReturn(quotaResult(false));

        assertThatThrownBy(() -> service.submitTask(submitDto())).isInstanceOf(RateLimitExceededException.class);

        verify(mapper, never()).insert(any());
        verify(stream, never()).add(eq(AudioConstant.REDIS_STREAM_KEY), any(Map.class));
    }

    @Test
    void callbackFinishRollsBackOnlyWhenCasFails() {
        AudioCallbackFinishDTO dto = new AudioCallbackFinishDTO(); dto.setTaskId(20L); dto.setStatus(AudioConstant.TASK_STATUS_SUCCESS);
        when(mapper.casUpdateStatus(any(), any(), any(), any(), any(), any())).thenReturn(0);
        when(mapper.getUserIdByTaskId(20L)).thenReturn(7L);

        assertThat(service.callbackFinish(dto)).isFalse();

        ArgumentCaptor<ConsumeQuotaCommand> rollback = ArgumentCaptor.forClass(ConsumeQuotaCommand.class);
        verify(quotaApi).rollback(rollback.capture());
        assertThat(rollback.getValue().userId()).isEqualTo(7L);
        assertThat(rollback.getValue().quotaType()).isEqualTo(QuotaType.DAILY_API_CALL);
        assertThat(rollback.getValue().amount()).isEqualTo(1L);

        when(mapper.casUpdateStatus(any(), any(), any(), any(), any(), any())).thenReturn(1);
        assertThat(service.callbackFinish(dto)).isTrue();
        verify(quotaApi, times(1)).rollback(any());
    }

    private static AudioTaskSubmitDTO submitDto() {
        AudioTaskSubmitDTO dto = new AudioTaskSubmitDTO();
        dto.setText("hello"); dto.setSpeed(new BigDecimal("1.2")); dto.setNoiseType(AudioConstant.NOISE_TYPE_PURE);
        return dto;
    }

    private static ConsumeQuotaResult quotaResult(boolean consumed) {
        return new ConsumeQuotaResult(consumed, new QuotaSnapshot(9L, QuotaType.DAILY_API_CALL, 10L, 0L, LocalDate.now()));
    }
}
