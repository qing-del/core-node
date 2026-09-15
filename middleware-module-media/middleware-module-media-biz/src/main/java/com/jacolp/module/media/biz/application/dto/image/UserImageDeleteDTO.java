package com.jacolp.module.media.biz.application.dto.image;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "用户端删除图片请求")
public class UserImageDeleteDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
}
