package com.jacolp.audio.biz.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.jacolp.audio.biz.domain.dto.AudioCallbackFinishDTO;
import com.jacolp.audio.biz.domain.dto.AudioCallbackStartDTO;
import com.jacolp.audio.biz.domain.dto.AudioTaskPageQueryDTO;
import com.jacolp.audio.biz.domain.dto.AudioTaskSubmitDTO;
import com.jacolp.context.BaseContext;
import com.jacolp.context.PermissionContext;
import com.jacolp.exception.BaseException;
import com.jacolp.exception.RateLimitExceededException;
import com.jacolp.audio.biz.constant.AudioConstant;
import com.jacolp.audio.biz.domain.vo.AudioTaskSubmitVO;
import com.jacolp.audio.biz.domain.vo.AudioTaskVO;
import com.jacolp.audio.biz.domain.vo.AudioTaskStatisticsVO;
import com.jacolp.audio.biz.audio.AudioTaskLifecycle;
import com.jacolp.audio.biz.persistence.dataobject.AudioTaskDO;
import com.jacolp.audio.biz.persistence.mapper.AudioTaskMapper;
import com.jacolp.module.system.api.quota.ConsumeQuotaCommand;
import com.jacolp.module.system.api.quota.ConsumeQuotaResult;
import com.jacolp.module.system.api.quota.UserQuotaApi;
import com.jacolp.result.PageResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AudioTaskServiceImpl implements AudioTaskService {
    private static final String STORAGE_INSUFFICIENT_ERROR = "存储空间不足";

    @Autowired
    private AudioTaskMapper audioTaskMapper;
    @Autowired
    private UserQuotaApi userQuotaApi;
    @Autowired
    private AudioTaskPublisher audioTaskPublisher;
    @Autowired
    private AudioResourceDeletePublisher audioResourceDeletePublisher;
    @Autowired
    private TransactionAfterCommitExecutor transactionAfterCommitExecutor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AudioTaskSubmitVO submitTask(AudioTaskSubmitDTO dto) {
        Long userId = BaseContext.getCurrentId();
        // 校验 noiseType 合法性
        if (!AudioConstant.VALID_NOISE_TYPES.contains(dto.getNoiseType()))
            throw new BaseException("不支持的背景音类型: " + dto.getNoiseType());
        // 检查每日配额
        checkDailyQuota(userId);
        // 构建任务实体
        AudioTaskDO task = new AudioTaskDO();
        task.setUserId(userId);
        task.setSourceText(dto.getText());
        task.setSpeed(dto.getSpeed());
        task.setNoiseType(dto.getNoiseType());
        task.setNoiseFactor(dto.getNoiseFactor() != null ? dto.getNoiseFactor() : BigDecimal.valueOf(AudioConstant.DEFAULT_NOISE_FACTOR));
        task.setStatus(AudioTaskLifecycle.initialStatus());
        task.setRetryTime(0);
        audioTaskMapper.insert(task);
        log.info("Audio task created, taskId: {}, userId: {}", task.getId(), userId);
        transactionAfterCommitExecutor.execute(() -> audioTaskPublisher.publish(task));
        return new AudioTaskSubmitVO(task.getId(), AudioTaskLifecycle.initialStatus());
    }

    @Override
    public boolean callbackStart(AudioCallbackStartDTO dto) {
        int updated = audioTaskMapper.casUpdateStatus(dto.getTaskId(), dto.getAttempt(),
                AudioTaskLifecycle.callbackStartExpectedStatus(),
                AudioTaskLifecycle.callbackStartResultStatus(), null, null, null, null);
        if (updated == 0) {
            log.warn("callbackStart CAS failed, taskId: {} (already processed or not found)", dto.getTaskId());
            return false;
        }
        log.info("Audio task started processing, taskId: {}", dto.getTaskId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean callbackFinish(AudioCallbackFinishDTO dto) {
        if (!AudioTaskLifecycle.isAllowedFinishStatus(dto.getStatus()))
            throw new BaseException("无效的回调状态值: " + dto.getStatus());
        if (dto.getStatus() == AudioTaskLifecycle.Status.SUCCESS.code())
            return finishSuccessfulTask(dto);
        int updated = audioTaskMapper.casUpdateStatus(dto.getTaskId(), dto.getAttempt(),
                AudioTaskLifecycle.callbackFinishExpectedStatus(),
                dto.getStatus(), dto.getResultUrl(), null, dto.getErrorMsg(), null);
        if (updated == 0) {
            log.warn("callbackFinish CAS failed, taskId: {} (already processed or not found)", dto.getTaskId());
            return false;
        }
        log.info("Audio task finished, taskId: {}, status: {}", dto.getTaskId(), dto.getStatus());
        return true;
    }

    private boolean finishSuccessfulTask(AudioCallbackFinishDTO dto) {
        if (dto.getAudioSize() == null || dto.getAudioSize() <= 0)
            throw new BaseException("成功回调必须携带大于 0 的 AudioSize");
        if (dto.getResultUrl() == null || dto.getResultUrl().isBlank())
            throw new BaseException("成功回调必须携带 resultUrl");

        AudioTaskDO task = audioTaskMapper.selectById(dto.getTaskId());
        if (task == null || task.getStatus() == null
                || task.getStatus() != AudioTaskLifecycle.callbackFinishExpectedStatus()
                || !Objects.equals(task.getRetryTime(), dto.getAttempt())) {
            log.warn("Successful callback rejected before quota consumption, taskId: {}", dto.getTaskId());
            return false;
        }

        ConsumeQuotaCommand storageCommand = ConsumeQuotaCommand.storageBytes(task.getUserId(), dto.getAudioSize());
        ConsumeQuotaResult storageResult = userQuotaApi.consume(storageCommand);
        if (!storageResult.consumed()) {
            audioTaskMapper.casUpdateStatus(dto.getTaskId(), dto.getAttempt(),
                    AudioTaskLifecycle.callbackFinishExpectedStatus(),
                    AudioTaskLifecycle.Status.FAILED.code(), null, null, STORAGE_INSUFFICIENT_ERROR, null);
            log.warn("Audio task rejected due to insufficient storage, taskId: {}, audioSize: {}, remaining: {}",
                    dto.getTaskId(), dto.getAudioSize(), storageResult.quota().remaining());
            return false;
        }

        int updated = audioTaskMapper.casUpdateStatus(dto.getTaskId(), dto.getAttempt(),
                AudioTaskLifecycle.callbackFinishExpectedStatus(), AudioTaskLifecycle.Status.SUCCESS.code(),
                dto.getResultUrl(), dto.getAudioSize(), null, LocalDate.now());
        if (updated == 0) {
            userQuotaApi.rollback(storageCommand);
            log.warn("Successful callback CAS failed after storage reservation, taskId: {}", dto.getTaskId());
            return false;
        }
        log.info("Audio task finished, taskId: {}, status: {}, audioSize: {}",
                dto.getTaskId(), dto.getStatus(), dto.getAudioSize());
        return true;
    }

    @Override
    public AudioTaskVO getTask(Long taskId) {
        Long userId = BaseContext.getCurrentId();
        AudioTaskDO task = audioTaskMapper.selectById(taskId);
        if (task == null || (!PermissionContext.isAdmin() && !task.getUserId().equals(userId)))
            throw new BaseException("任务不存在或无权访问");
        AudioTaskVO vo = new AudioTaskVO();
        BeanUtils.copyProperties(task, vo);
        return vo;
    }

    @Override
    public PageResult listTasks(AudioTaskPageQueryDTO dto) {
        // 非管理员只能查询自己的任务，管理员可按 userId 筛选或查询全部
        if (!PermissionContext.isAdmin()) dto.setUserId(BaseContext.getCurrentId());
        PageHelper.startPage(dto.getPageNumOrDefault(), dto.getPageSizeOrDefault());
        List<AudioTaskDO> list = audioTaskMapper.selectByUserId(dto);
        Page<AudioTaskDO> page = (Page<AudioTaskDO>) list;
        List<AudioTaskVO> values = list.stream().map(task -> {
            AudioTaskVO vo = new AudioTaskVO();
            BeanUtils.copyProperties(task, vo);
            return vo;
        }).collect(Collectors.toList());
        return new PageResult(page.getTotal(), values);
    }

    @Override
    public AudioTaskStatisticsVO getStatistics() {
        return audioTaskMapper.selectStatistics();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelTask(Long taskId) {
        Long userId = PermissionContext.isAdmin() ? null : BaseContext.getCurrentId();
        int updated = audioTaskMapper.cancelTask(taskId, userId, AudioTaskLifecycle.cancelledStatus());
        if (updated == 0)
            throw new BaseException("任务不存在、无权访问或当前状态不可取消");
        log.info("Audio task cancelled, taskId: {}, operatorUserId: {}, admin: {}",
                taskId, BaseContext.getCurrentId(), PermissionContext.isAdmin());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTask(Long taskId) {
        Long userId = PermissionContext.isAdmin() ? null : BaseContext.getCurrentId();
        AudioTaskDO task = audioTaskMapper.selectById(taskId);
        if (task == null || (userId != null && !task.getUserId().equals(userId)))
            throw new BaseException("任务不存在或无权删除");
        if (audioTaskMapper.deleteTask(taskId, userId) == 0)
            throw new BaseException("任务不存在或无权删除");
        if (task.getAudioSize() != null && task.getAudioSize() > 0)
            userQuotaApi.rollback(ConsumeQuotaCommand.storageBytes(task.getUserId(), task.getAudioSize()));
        transactionAfterCommitExecutor.execute(() -> audioResourceDeletePublisher.publish(task));
        log.info("Audio task deleted, taskId: {}, ownerUserId: {}, admin: {}",
                taskId, task.getUserId(), PermissionContext.isAdmin());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AudioTaskSubmitVO retryFailedTask(Long taskId) {
        Long userId = BaseContext.getCurrentId();
        AudioTaskDO task = audioTaskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId))
            throw new BaseException("任务不存在或无权访问");

        int updated = audioTaskMapper.markTaskRetried(taskId, userId,
                AudioTaskLifecycle.Status.FAILED.code(), AudioTaskLifecycle.Status.RETRIED.code());
        if (updated == 0)
            throw new BaseException("仅失败状态的任务可以重试");

        AudioTaskDO retryTask = new AudioTaskDO();
        retryTask.setUserId(userId);
        retryTask.setSourceText(task.getSourceText());
        retryTask.setSpeed(task.getSpeed());
        retryTask.setNoiseType(task.getNoiseType());
        retryTask.setNoiseFactor(task.getNoiseFactor());
        retryTask.setStatus(AudioTaskLifecycle.initialStatus());
        retryTask.setRetryTime(0);
        audioTaskMapper.insert(retryTask);
        transactionAfterCommitExecutor.execute(() -> audioTaskPublisher.publish(retryTask));
        log.info("Audio task retried, sourceTaskId: {}, retryTaskId: {}, userId: {}",
                taskId, retryTask.getId(), userId);
        return new AudioTaskSubmitVO(retryTask.getId(), AudioTaskLifecycle.initialStatus());
    }

    @Override
    public void requeueTask(AudioTaskDO task) {
        audioTaskPublisher.publish(task);
    }

    /**
     * 检查用户今日 API 调用次数。
     */
    private void checkDailyQuota(Long userId) {
        LocalDate today = LocalDate.now();
        ConsumeQuotaResult result = userQuotaApi.consume(ConsumeQuotaCommand.dailyApiCall(userId, 1L, today));
        if (!result.consumed())
            throw new RateLimitExceededException(String.format("今日 API 调用次数已达上限（%d 次），请明日再试", result.quota().limit()));
        // 原子递增（并发安全）
    }
}
