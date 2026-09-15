package com.jacolp.audio.biz.controller.admin;

import com.jacolp.audio.biz.domain.dto.AudioTaskPageQueryDTO;
import com.jacolp.audio.biz.domain.vo.AudioTaskVO;
import com.jacolp.audio.biz.domain.vo.AudioTaskStatisticsVO;
import com.jacolp.audio.biz.service.AudioTaskService;
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

@RestController("Admin-AudioController")
@RequestMapping("/admin/audio")
@Slf4j
@CrossOrigin("*")
@Validated
@Schema(description = "Admin - 音频任务回调")
@Tag(name = "Admin-音频任务", description = "管理端用于查看音频任务的接口")
public class AudioController {
    @Autowired
    private AudioTaskService audioTaskService;

    @PostMapping("/list")
    @Operation(summary = "分页查询任务列表", description = "按任务状态、任务创建时间分页查询任务列表。")
    public Result<PageResult> listTasks(@Parameter(description = "分页参数") @Valid @RequestBody AudioTaskPageQueryDTO queryDTO) {
        log.info("List tasks, page: {}, size: {}", queryDTO.getPageNumOrDefault(), queryDTO.getPageSizeOrDefault());
        return Result.success(audioTaskService.listTasks(queryDTO));
    }

    @GetMapping("/statistics")
    @Operation(summary = "查询今日音频任务统计", description = "返回今日成功、今日失败、当前等待和当前处理中的任务数量。")
    public Result<AudioTaskStatisticsVO> getStatistics() {
        return Result.success(audioTaskService.getStatistics());
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "查询音频任务详情", description = "管理员可查询任意用户的音频任务参数、状态、结果 URL 和失败原因。")
    public Result<AudioTaskVO> getTask(
            @Parameter(description = "任务 ID") @PathVariable Long taskId) {
        log.info("Admin query audio task detail, taskId: {}", taskId);
        return Result.success(audioTaskService.getTask(taskId));
    }

    @PostMapping("/cancel/{taskId}")
    @Operation(summary = "取消音频任务", description = "管理员可取消任意用户处于 PENDING 或 PROCESSING 状态的任务。")
    public Result<Boolean> cancelTask(
            @Parameter(description = "任务 ID") @PathVariable Long taskId) {
        return Result.success(audioTaskService.cancelTask(taskId));
    }

    @DeleteMapping("/{taskId}")
    @Operation(summary = "删除音频任务", description = "管理员可删除任意用户的任务，并通过 MQ 通知 Python 清理对应资源。")
    public Result<Boolean> deleteTask(
            @Parameter(description = "任务 ID") @PathVariable Long taskId) {
        return Result.success(audioTaskService.deleteTask(taskId));
    }
}
