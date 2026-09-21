package com.jacolp.note.application.vo.note;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class NoteRelationDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long noteId;

    private List<NoteTagMappingRowVO> tags;

    private List<NoteImageMappingRowVO> images;

    private List<NoteEachMappingRowVO> eachNotes;
}
