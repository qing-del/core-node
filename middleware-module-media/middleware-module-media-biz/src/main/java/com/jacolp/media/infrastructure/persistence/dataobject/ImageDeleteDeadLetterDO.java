package com.jacolp.media.infrastructure.persistence.dataobject;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Persistence model for {@code biz_image_delete_dead_letter}. */
@Data
@NoArgsConstructor
public class ImageDeleteDeadLetterDO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String resourceId;
    private String imageUrl;
    private String eventId;
    private Short status;
    private Integer retryCount;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime completedTime;
}
