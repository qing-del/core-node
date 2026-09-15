package com.jacolp.note.application.dto.tag;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class TagMappingBindDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "标签映射行 ID 不能为空！")
    @Positive(message = "标签映射行 ID 必须为正数！")
    private Long mappingId;

    @NotBlank(message = "标签 ID 不能为空！")
    @Positive(message = "标签 ID 必须为正数！")
    private Long tagId;
}
