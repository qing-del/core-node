package com.jacolp.note.application.vo.note;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class NoteEachSimpleVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long targetNoteId;
    private String targetNoteTitle;
    private String parsedNoteName;
    private String anchor;
    private String nickname;
    private Short isMissing;
}
