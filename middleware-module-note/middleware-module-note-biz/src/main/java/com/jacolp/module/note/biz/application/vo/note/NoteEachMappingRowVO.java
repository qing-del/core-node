package com.jacolp.module.note.biz.application.vo.note;

import java.io.Serializable;

import lombok.Data;

@Data
public class NoteEachMappingRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long mappingId;

    private Long sourceNoteId;

    private Long targetNoteId;

    private String parsedNoteName;

    private String targetNoteTitle;

    private String anchor;

    private String nickname;

    private Short status;

    private Short isMissing;
}
