package com.jacolp.media.application.vo.image;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户端图片详情信息")
public class UserImageDetailVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String filename;
    private String ossUrl;
    private Long fileSize;
    private LocalDateTime uploadTime;
    private Short isPublic;
}
