package com.jacolp.middleware.module.audio.biz.application.service;

import com.jacolp.audio.biz.service.AudioTaskServiceImpl;
import com.jacolp.audio.biz.service.AudioTaskPublisher;
import com.jacolp.audio.biz.service.AudioResourceDeletePublisher;
import com.jacolp.audio.biz.service.TransactionAfterCommitExecutor;
import com.jacolp.audio.biz.audio.AudioTaskLifecycle;
import com.jacolp.context.BaseContext;
import com.jacolp.context.PermissionContext;
import com.jacolp.exception.BaseException;
import com.jacolp.exception.RateLimitExceededException;
import com.jacolp.audio.biz.constant.AudioConstant;
import com.jacolp.audio.biz.domain.dto.AudioCallbackFinishDTO;
import com.jacolp.audio.biz.domain.dto.AudioCallbackStartDTO;
import com.jacolp.audio.biz.domain.dto.AudioTaskSubmitDTO;
import com.jacolp.audio.biz.persistence.dataobject.AudioTaskDO;
import com.jacolp.audio.biz.persistence.mapper.AudioTaskMapper;
import com.jacolp.audio.biz.domain.vo.AudioTaskStatisticsVO;
import com.jacolp.module.system.api.quota.ConsumeQuotaCommand;
import com.jacolp.module.system.api.quota.ConsumeQuotaResult;
import com.jacolp.module.system.api.quota.QuotaSnapshot;
import com.jacolp.module.system.api.quota.QuotaType;
import com.jacolp.module.system.api.quota.UserQuotaApi;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioTaskServiceImplTest {
    private AudioTaskMapper mapper;
    private UserQuotaApi quotaApi;
    private AudioTaskPublisher publisher;
    private AudioResourceDeletePublisher deletePublisher;
    private TransactionAfterCommitExecutor afterCommitExecutor;
    private AudioTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(AudioTaskMapper.class);
        quotaApi = mock(UserQuotaApi.class);
        publisher = mock(AudioTaskPublisher.class);
        deletePublisher = mock(AudioResourceDeletePublisher.class);
        afterCommitExecutor = mock(TransactionAfterCommitExecutor.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(afterCommitExecutor).execute(any());
        service = new AudioTaskServiceImpl();
        ReflectionTestUtils.setField(service, "audioTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "userQuotaApi", quotaApi);
        ReflectionTestUtils.setField(service, "audioTaskPublisher", publisher);
        ReflectionTestUtils.setField(service, "audioResourceDeletePublisher", deletePublisher);
        ReflectionTestUtils.setField(service, "transactionAfterCommitExecutor", afterCommitExecutor);
        BaseContext.setCurrentId(9L);
    }

    @AfterEach
    void clearContext() {
        BaseContext.remove();
        PermissionContext.remove();
    }

    @Test
    void userCanReadOnlyOwnTaskWhileAdminCanReadAnyTask() {
        AudioTaskDO task = new AudioTaskDO();
        task.setId(30L);
        task.setUserId(7L);
        task.setSourceText("detail text");
        task.setResultUrl("https://audio.example/30.mp3");
        when(mapper.selectById(30L)).thenReturn(task);

        assertThatThrownBy(() -> service.getTask(30L)).isInstanceOf(BaseException.class);

        PermissionContext.setAdmin(true);
        assertThat(service.getTask(30L).getSourceText()).isEqualTo("detail text");
        assertThat(service.getTask(30L).getResultUrl()).isEqualTo("https://audio.example/30.mp3");
    }

    @Test
    void returnsAudioTaskStatisticsFromPersistence() {
        AudioTaskStatisticsVO statistics = new AudioTaskStatisticsVO(8L, 3L, 5L, 2L);
        when(mapper.selectStatistics()).thenReturn(statistics);

        assertThat(service.getStatistics()).isSameAs(statistics);
        verify(mapper).selectStatistics();
    }

    @Test
    void cancelScopesUserUpdatesAndLetsAdminOperateAcrossUsers() {
        when(mapper.cancelTask(31L, 9L, AudioTaskLifecycle.Status.CANCELLED.code())).thenReturn(1);

        assertThat(service.cancelTask(31L)).isTrue();
        verify(mapper).cancelTask(31L, 9L, AudioTaskLifecycle.Status.CANCELLED.code());

        PermissionContext.setAdmin(true);
        when(mapper.cancelTask(32L, null, AudioTaskLifecycle.Status.CANCELLED.code())).thenReturn(1);
        assertThat(service.cancelTask(32L)).isTrue();
        verify(mapper).cancelTask(32L, null, AudioTaskLifecycle.Status.CANCELLED.code());
    }

    @Test
    void cancelRejectsMissingUnauthorizedOrTerminalTask() {
        when(mapper.cancelTask(33L, 9L, AudioTaskLifecycle.Status.CANCELLED.code())).thenReturn(0);

        assertThatThrownBy(() -> service.cancelTask(33L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("不可取消");
    }

    @Test
    void userDeletesOwnTaskAndPublishesCleanupAfterDeletion() {
        AudioTaskDO task = new AudioTaskDO();
        task.setId(40L);
        task.setUserId(9L);
        task.setResultUrl("https://audio.example/40.mp3");
        task.setAudioSize(300L);
        when(mapper.selectById(40L)).thenReturn(task);
        when(mapper.deleteTask(40L, 9L)).thenReturn(1);

        assertThat(service.deleteTask(40L)).isTrue();

        verify(mapper).deleteTask(40L, 9L);
        ArgumentCaptor<ConsumeQuotaCommand> releasedStorage = ArgumentCaptor.forClass(ConsumeQuotaCommand.class);
        verify(quotaApi).rollback(releasedStorage.capture());
        assertThat(releasedStorage.getValue().quotaType()).isEqualTo(QuotaType.STORAGE_BYTES);
        assertThat(releasedStorage.getValue().amount()).isEqualTo(300L);
        verify(deletePublisher).publish(task);
    }

    @Test
    void deleteRejectsTaskOwnedByAnotherUserWithoutPublishing() {
        AudioTaskDO task = new AudioTaskDO();
        task.setId(41L);
        task.setUserId(7L);
        when(mapper.selectById(41L)).thenReturn(task);

        assertThatThrownBy(() -> service.deleteTask(41L)).isInstanceOf(BaseException.class);

        verify(mapper, never()).deleteTask(any(), any());
        verify(deletePublisher, never()).publish(any());
    }

    @Test
    void adminDeletesAnyUsersTaskWithoutOwnerSqlFilter() {
        AudioTaskDO task = new AudioTaskDO();
        task.setId(42L);
        task.setUserId(7L);
        when(mapper.selectById(42L)).thenReturn(task);
        when(mapper.deleteTask(42L, null)).thenReturn(1);
        PermissionContext.setAdmin(true);

        assertThat(service.deleteTask(42L)).isTrue();

        verify(mapper).deleteTask(42L, null);
        verify(deletePublisher).publish(task);
    }

    @Test
    void submitConsumesQuotaInsertsPendingTaskAndPublishesIt() {
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
        assertThat(task.getValue().getRetryTime()).isZero();
        assertThat(task.getValue().getNoiseFactor()).isEqualByComparingTo("0.5");
        verify(publisher).publish(task.getValue());
    }

    @Test
    void callbackStartUsesAttemptAsPartOfCompareAndSet() {
        AudioCallbackStartDTO dto = new AudioCallbackStartDTO();
        dto.setTaskId(19L);
        dto.setAttempt(2);
        when(mapper.casUpdateStatus(19L, 2, 0, 1,
                null, null, null, null)).thenReturn(1);

        assertThat(service.callbackStart(dto)).isTrue();

        verify(mapper).casUpdateStatus(19L, 2, 0, 1,
                null, null, null, null);
    }

    @Test
    void rejectedQuotaDoesNotWriteDatabaseOrStream() {
        when(quotaApi.consume(any())).thenReturn(quotaResult(false));

        assertThatThrownBy(() -> service.submitTask(submitDto())).isInstanceOf(RateLimitExceededException.class);

        verify(mapper, never()).insert(any());
        verify(publisher, never()).publish(any());
    }

    @Test
    void successfulCallbackConsumesStorageAndPersistsAudioSize() {
        AudioCallbackFinishDTO dto = successfulCallback(20L, 120L);
        when(mapper.selectById(20L)).thenReturn(processingTask(20L, 7L));
        when(quotaApi.consume(any())).thenReturn(storageQuotaResult(true, 120L, 1_000L));
        when(mapper.casUpdateStatus(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        assertThat(service.callbackFinish(dto)).isTrue();

        ArgumentCaptor<ConsumeQuotaCommand> consumed = ArgumentCaptor.forClass(ConsumeQuotaCommand.class);
        verify(quotaApi).consume(consumed.capture());
        assertThat(consumed.getValue().userId()).isEqualTo(7L);
        assertThat(consumed.getValue().quotaType()).isEqualTo(QuotaType.STORAGE_BYTES);
        assertThat(consumed.getValue().amount()).isEqualTo(120L);
        verify(mapper).casUpdateStatus(eq(20L), eq(0), eq(1), eq(2),
                eq("https://audio.example/20.mp3"), eq(120L), eq(null), any(LocalDate.class));
        verify(quotaApi, never()).rollback(any());
    }

    @Test
    void insufficientStorageMarksTaskFailedAndReturnsFalse() {
        AudioCallbackFinishDTO dto = successfulCallback(21L, 120L);
        when(mapper.selectById(21L)).thenReturn(processingTask(21L, 7L));
        when(quotaApi.consume(any())).thenReturn(storageQuotaResult(false, 90L, 100L));
        when(mapper.casUpdateStatus(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        assertThat(service.callbackFinish(dto)).isFalse();

        verify(mapper).casUpdateStatus(21L, 0, 1, -1,
                null, null, "存储空间不足", null);
        verify(quotaApi, never()).rollback(any());
    }

    @Test
    void successfulCallbackRollsBackStorageWhenTaskWasConcurrentlyCancelled() {
        AudioCallbackFinishDTO dto = successfulCallback(22L, 120L);
        when(mapper.selectById(22L)).thenReturn(processingTask(22L, 7L));
        when(quotaApi.consume(any())).thenReturn(storageQuotaResult(true, 120L, 1_000L));
        when(mapper.casUpdateStatus(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0);

        assertThat(service.callbackFinish(dto)).isFalse();

        ArgumentCaptor<ConsumeQuotaCommand> rollback = ArgumentCaptor.forClass(ConsumeQuotaCommand.class);
        verify(quotaApi).rollback(rollback.capture());
        assertThat(rollback.getValue().quotaType()).isEqualTo(QuotaType.STORAGE_BYTES);
        assertThat(rollback.getValue().amount()).isEqualTo(120L);
    }

    @Test
    void staleSuccessfulCallbackIsRejectedBeforeStorageConsumption() {
        AudioCallbackFinishDTO dto = successfulCallback(24L, 120L);
        dto.setAttempt(0);
        AudioTaskDO newerAttempt = processingTask(24L, 7L);
        newerAttempt.setRetryTime(1);
        when(mapper.selectById(24L)).thenReturn(newerAttempt);

        assertThat(service.callbackFinish(dto)).isFalse();

        verify(quotaApi, never()).consume(any());
        verify(mapper, never()).casUpdateStatus(any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void successfulCallbackRequiresAudioSize() {
        AudioCallbackFinishDTO dto = successfulCallback(23L, null);

        assertThatThrownBy(() -> service.callbackFinish(dto))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("AudioSize");

        verify(quotaApi, never()).consume(any());
    }

    private static AudioTaskSubmitDTO submitDto() {
        AudioTaskSubmitDTO dto = new AudioTaskSubmitDTO();
        dto.setText("hello"); dto.setSpeed(new BigDecimal("1.2")); dto.setNoiseType(AudioConstant.NOISE_TYPE_PURE);
        return dto;
    }

    private static ConsumeQuotaResult quotaResult(boolean consumed) {
        return new ConsumeQuotaResult(consumed, new QuotaSnapshot(9L, QuotaType.DAILY_API_CALL, 10L, 0L, LocalDate.now()));
    }

    private static AudioCallbackFinishDTO successfulCallback(Long taskId, Long audioSize) {
        AudioCallbackFinishDTO dto = new AudioCallbackFinishDTO();
        dto.setTaskId(taskId);
        dto.setAttempt(0);
        dto.setStatus(AudioConstant.TASK_STATUS_SUCCESS);
        dto.setResultUrl("https://audio.example/" + taskId + ".mp3");
        dto.setAudioSize(audioSize);
        return dto;
    }

    private static AudioTaskDO processingTask(Long taskId, Long userId) {
        AudioTaskDO task = new AudioTaskDO();
        task.setId(taskId);
        task.setUserId(userId);
        task.setStatus(AudioTaskLifecycle.Status.PROCESSING.code());
        task.setRetryTime(0);
        return task;
    }

    private static ConsumeQuotaResult storageQuotaResult(boolean consumed, long used, long limit) {
        return new ConsumeQuotaResult(consumed,
                new QuotaSnapshot(7L, QuotaType.STORAGE_BYTES, limit, used, null));
    }
}
