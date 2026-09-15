package com.jacolp.module.media.biz.application.vo.image;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long userId;
    private Long topicId;
    private String filename;
    private String ossUrl;
    private Short storageType;
    private Long fileSize;
    private Short isPublic;
    private Short auditStatus;
    private LocalDateTime uploadTime;
}
