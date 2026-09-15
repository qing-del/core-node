package com.jacolp.audio.biz.controller.admin;

import com.jacolp.audio.biz.domain.dto.AudioTaskPageQueryDTO;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("Admin-AudioController")
@RequestMapping("/admin/audio")
@Slf4j @CrossOrigin("*") @Validated
@Schema(description = "Admin - 音频任务回调")
@Tag(name = "Admin-音频任务", description = "管理端用于查看音频任务的接口")
public class AudioController {
    @Autowired private AudioTaskService audioTaskService;

    @PostMapping("/list")
    @Operation(summary = "分页查询任务列表", description = "按任务状态、任务创建时间分页查询任务列表。")
    public Result<PageResult> listTasks(@Parameter(description = "分页参数") @Valid @RequestBody AudioTaskPageQueryDTO queryDTO) {
        log.info("List tasks, page: {}, size: {}", queryDTO.getPageNumOrDefault(), queryDTO.getPageSizeOrDefault());
        return Result.success(audioTaskService.listTasks(queryDTO));
    }
}
