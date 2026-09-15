package com.jacolp.module.note.biz.application.vo.note;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class NoteChangeDiffVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long noteId;
    private Integer status;
    private Long oldFileSize;
    private Long newFileSize;
    private Long diffFileSize;
    private NoteDiffVO diff;
}
