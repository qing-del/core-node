package com.jacolp.media.application.dto.image;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "用户端获取图片详情请求")
public class UserImageDetailDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
}
