package com.jacolp.media.infrastructure.persistence.dataobject;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Persistence model for {@code biz_image}; it must not escape the media module. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageDO implements Serializable {
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
