package com.jacolp.note.application.vo.image;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageSimpleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long imageId;

    private Long noteId;

    private String parsedImageName;

    private String filename;

    private String ossUrl;

    private Short isPublic;

    private Short status;

    private Short isCrossUser;

    private Short isMissing;

    private LocalDateTime createTime;
}
