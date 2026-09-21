package com.jacolp.note.application.dto.tag;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户端创建标签 DTO
 */
@Data
@Schema(description = "创建标签请求")
public class UserTagAddDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "标签名称（最多20个字符）", example = "Java", required = true)
    private String tagName;
}
