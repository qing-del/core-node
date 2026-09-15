package com.jacolp.audio.biz.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Schema(description = "音频任务提交响应")
public class AudioTaskSubmitVO {
    @Schema(description = "任务ID") private Long taskId;
    @Schema(description = "任务状态：0=排队中") private Integer status;
}
