package com.jacolp.module.media.biz.application.vo.image;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

/** JSON-compatible replacement for the former NoteSimpleVO at /admin/image/notes. */
@Data @NoArgsConstructor @AllArgsConstructor
public class ImageNoteSimpleVO implements Serializable {
    private Long id;
    private String title;
    private Short isCrossUser;
    private Short status;
    private LocalDateTime createTime;
}
