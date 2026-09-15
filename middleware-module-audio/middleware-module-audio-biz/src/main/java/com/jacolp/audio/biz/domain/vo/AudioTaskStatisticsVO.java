package com.jacolp.audio.biz.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理端音频任务统计")
public class AudioTaskStatisticsVO {
    @Schema(description = "今日生成成功的任务数")
    private Long todaySuccessCount;
    @Schema(description = "今日生成失败的任务数")
    private Long todayFailedCount;
    @Schema(description = "当前等待处理的任务数")
    private Long pendingCount;
    @Schema(description = "当前正在处理的任务数")
    private Long processingCount;
}
