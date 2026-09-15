package com.jacolp.module.note.biz.application.vo.note;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class NoteUploadVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long noteId;
    private List<String> missingTags;
    private List<String> missingImages;
    private List<String> missingNoteNames;
}
