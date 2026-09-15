package com.jacolp.audio.biz.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data @Schema(description = "Python 回调 B：任务完成/失败")
public class AudioCallbackFinishDTO {
    @NotNull(message = "taskId 不能为空") @Schema(description = "任务ID", requiredMode = Schema.RequiredMode.REQUIRED) private Long taskId;
    @NotNull(message = "attempt 不能为空")
    @PositiveOrZero(message = "attempt 不能小于 0")
    @Schema(description = "队列消息中的处理轮次", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer attempt;
    @NotNull(message = "status 不能为空") @Schema(description = "任务结果状态：2=成功, -1=失败", requiredMode = Schema.RequiredMode.REQUIRED) private Integer status;
    @Schema(description = "成功时的音频下载链接") private String resultUrl;
    @JsonProperty("AudioSize")
    @JsonAlias("audioSize")
    @Positive(message = "AudioSize 必须大于 0")
    @Schema(description = "成功时生成的音频文件大小，单位为字节")
    private Long audioSize;
    @Schema(description = "失败时的错误信息") private String errorMsg;
}
