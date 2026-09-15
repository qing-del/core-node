package com.jacolp.audio.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
@Schema(description = "音频生成任务提交请求")
public class AudioTaskSubmitDTO {
    @NotBlank(message = "文本内容不能为空") @Schema(description = "待生成的纯文本内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String text;
    @NotNull(message = "语速不能为空") @DecimalMin(value = "0.5", message = "语速最小为 0.5") @DecimalMax(value = "3.0", message = "语速最大为 3.0")
    @Schema(description = "播放倍速 (0.5 ~ 3.0)", requiredMode = Schema.RequiredMode.REQUIRED) private BigDecimal speed;
    @NotBlank(message = "背景音类型不能为空") @Schema(description = "背景音标识符 (PURE/WHITE_NOISE/PINK_NOISE/BROWN_NOISE/CAFE/AIRPORT/SUBWAY)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String noiseType;
    @DecimalMin(value = "0.0", message = "噪音因子最小为 0.0") @DecimalMax(value = "2.0", message = "噪音因子最大为 2.0")
    @Schema(description = "背景音量因子 (0.0 ~ 2.0)，默认 0.5") private BigDecimal noiseFactor;
}
