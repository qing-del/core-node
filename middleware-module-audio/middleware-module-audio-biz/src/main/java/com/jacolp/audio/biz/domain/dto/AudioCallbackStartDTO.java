package com.jacolp.audio.biz.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data @Schema(description = "Python 回调 A：任务开始处理")
public class AudioCallbackStartDTO { @NotNull(message = "taskId 不能为空") @Schema(description = "任务ID", requiredMode = Schema.RequiredMode.REQUIRED) private Long taskId; }
