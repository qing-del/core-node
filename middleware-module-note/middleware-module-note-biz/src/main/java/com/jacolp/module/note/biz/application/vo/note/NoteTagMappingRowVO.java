package com.jacolp.module.note.biz.application.vo.note;

import java.io.Serializable;

import lombok.Data;

@Data
public class NoteTagMappingRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long mappingId;

    private Long noteId;

    private Long tagId;

    private String parsedTagName;

    private String tagName;

    private Short status;

    private Short isMissing;
}
