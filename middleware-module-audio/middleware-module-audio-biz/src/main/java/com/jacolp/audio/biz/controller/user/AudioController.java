package com.jacolp.audio.biz.controller.user;

import com.jacolp.audio.biz.domain.dto.AudioTaskPageQueryDTO;
import com.jacolp.audio.biz.domain.dto.AudioTaskSubmitDTO;
import com.jacolp.audio.biz.service.AudioTaskService;
import com.jacolp.audio.biz.domain.vo.AudioTaskSubmitVO;
import com.jacolp.constant.RateLimitConstant;
import com.jacolp.web.annotation.RateLimit;
import com.jacolp.audio.biz.domain.vo.AudioTaskVO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("User-AudioController")
@RequestMapping("/user/audio")
@Slf4j @CrossOrigin("*") @Validated
@Schema(description = "User - 音频生成")
@Tag(name = "User-音频生成", description = "用户端音频生成任务接口")
public class AudioController {
    @Autowired private AudioTaskService audioTaskService;

    @PostMapping("/generate")
    @RateLimit(windowSeconds = 60, maxRequests = 5, prefix = RateLimitConstant.AUDIO_TASK_RATE_LIMIT_KEY)
    @Operation(summary = "提交音频生成任务", description = "提交文本转语音任务，指定语速、背景音类型和噪音因子，任务异步处理，返回 taskId 供后续轮询。")
    public Result<AudioTaskSubmitVO> generate(@RequestBody @Valid AudioTaskSubmitDTO dto) {
        log.info("User submit audio task, noiseType: {}, speed: {}", dto.getNoiseType(), dto.getSpeed());
        return Result.success(audioTaskService.submitTask(dto));
    }

    @PostMapping("/retry/{taskId}")
    @Operation(summary = "重试失败的音频任务", description = "仅允许重试当前用户处于失败状态的任务；原任务将标记为已重试，并创建新任务进入待处理队列。")
    public Result<AudioTaskSubmitVO> retry(@Parameter(description = "任务 ID") @PathVariable Long taskId) {
        log.info("User retry audio task, taskId: {}", taskId);
        return Result.success(audioTaskService.retryFailedTask(taskId));
    }

    @GetMapping("/status/{taskId}")
    @Operation(summary = "查询音频任务状态", description = "根据 taskId 查询任务当前状态与结果链接，仅能查询当前用户自己的任务。")
    public Result<AudioTaskVO> getStatus(@Parameter(description = "任务ID") @PathVariable Long taskId) {
        log.info("User query audio task status, taskId: {}", taskId);
        return Result.success(audioTaskService.getTask(taskId));
    }

    @PostMapping("/list")
    @Operation(summary = "分页查询当前用户音频任务列表")
    public Result<PageResult> listTasks(@Parameter(description = "分页参数") @Valid @RequestBody AudioTaskPageQueryDTO queryDTO) {
        log.info("User list audio tasks, page: {}, size: {}", queryDTO.getPageNum(), queryDTO.getPageSize());
        return Result.success(audioTaskService.listTasks(queryDTO));
    }

    @PostMapping("/cancel/{taskId}")
    @Operation(summary = "取消音频任务", description = "仅允许取消当前用户处于 PENDING 或 PROCESSING 状态的任务。")
    public Result<Boolean> cancelTask(
            @Parameter(description = "任务 ID") @PathVariable Long taskId) {
        return Result.success(audioTaskService.cancelTask(taskId));
    }

    @DeleteMapping("/{taskId}")
    @Operation(summary = "删除音频任务", description = "删除当前用户的任务，并通过 MQ 通知 Python 清理对应资源。")
    public Result<Boolean> deleteTask(
            @Parameter(description = "任务 ID") @PathVariable Long taskId) {
        return Result.success(audioTaskService.deleteTask(taskId));
    }
}
