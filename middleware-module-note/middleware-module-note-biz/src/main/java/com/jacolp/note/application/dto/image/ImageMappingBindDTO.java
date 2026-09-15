package com.jacolp.note.application.dto.image;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ImageMappingBindDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "图片映射行 ID 不能为空！")
    @Positive(message = "图片映射行 ID 必须为正数！")
    private Long mappingId;

    @NotBlank(message = "图片 ID 不能为空！")
    @Positive(message = "图片 ID 必须为正数！")
    private Long imageId;
}
