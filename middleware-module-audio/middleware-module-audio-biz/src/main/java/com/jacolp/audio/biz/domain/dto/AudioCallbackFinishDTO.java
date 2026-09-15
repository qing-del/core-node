package com.jacolp.audio.biz.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data @Schema(description = "Python 回调 B：任务完成/失败")
public class AudioCallbackFinishDTO {
    @NotNull(message = "taskId 不能为空") @Schema(description = "任务ID", requiredMode = Schema.RequiredMode.REQUIRED) private Long taskId;
    @NotNull(message = "status 不能为空") @Schema(description = "任务结果状态：2=成功, -1=失败", requiredMode = Schema.RequiredMode.REQUIRED) private Integer status;
    @Schema(description = "成功时的音频下载链接") private String resultUrl;
    @Schema(description = "失败时的错误信息") private String errorMsg;
}
