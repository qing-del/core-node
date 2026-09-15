package com.jacolp.module.note.biz.application.dto.tag;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户端解除标签绑定 DTO
 */
@Data
@Schema(description = "解除标签绑定请求")
public class UserTagRemoveDTO implements Serializable {

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
