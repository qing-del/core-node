package com.jacolp.note.application.dto.tag;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户端绑定标签 DTO
 */
@Data
@Schema(description = "绑定标签请求")
public class UserTagAssignDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "标签ID", example = "1", required = true)
    @NotNull(message = "标签ID无效")
    @Positive(message = "标签ID无效")
    private Long tagId;

    @Schema(description = "目标资源ID（笔记ID）", example = "1", required = true)
    @NotNull(message = "目标资源ID无效")
    @Positive(message = "目标资源ID无效")
    private Long targetId;
}
