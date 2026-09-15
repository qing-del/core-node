package com.jacolp.module.note.biz.application.vo.tag;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户端标签列表响应 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户端标签信息")
public class UserTagSimpleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "标签ID", example = "1")
    private Long id;

    @Schema(description = "标签名称", example = "Java")
    private String tagName;

    @Schema(description = "创建时间", example = "2024-01-15T10:30:00")
    private LocalDateTime createTime;
}
