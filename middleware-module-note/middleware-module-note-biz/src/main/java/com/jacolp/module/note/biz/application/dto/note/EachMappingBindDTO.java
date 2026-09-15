package com.jacolp.module.note.biz.application.dto.note;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class EachMappingBindDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "笔记映射行 ID 不能为空！")
    @Positive(message = "笔记映射行 ID 必须为正数！")
    private Long mappingId;

    @NotBlank(message = "笔记 ID 不能为空！")
    @Positive(message = "笔记 ID 必须为正数！")
    private Long noteId;
}
