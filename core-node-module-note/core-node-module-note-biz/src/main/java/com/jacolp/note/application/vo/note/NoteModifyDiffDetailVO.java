package com.jacolp.note.application.vo.note;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class NoteModifyDiffDetailVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long noteId;
    private String oldSource;
    private String newSource;
    private NoteChangeDiffVO diff;
}
