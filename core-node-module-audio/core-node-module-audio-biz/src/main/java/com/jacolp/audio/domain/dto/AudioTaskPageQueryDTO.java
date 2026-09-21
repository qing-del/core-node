package com.jacolp.audio.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class AudioTaskPageQueryDTO implements PageParamProvider {
    @Positive(message = "用户ID不能小于0") private Long userId;
    @Positive(message = "页码不能小于0") private Integer pageNum;
    @Positive(message = "页大小不能小于0") private Integer pageSize;
    @Schema(description = "任务状态筛选（null=不过滤）") private Integer status;
}
