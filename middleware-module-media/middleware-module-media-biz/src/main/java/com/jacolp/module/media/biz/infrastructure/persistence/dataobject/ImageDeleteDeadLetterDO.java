package com.jacolp.module.media.biz.infrastructure.persistence.dataobject;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Persistence model for {@code biz_image_delete_dead_letter}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageDeleteDeadLetterDO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String imageUrl;
    private Short status;
    private Integer retryCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
