package com.jacolp.module.note.biz.application.vo.note;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class NoteConvertMetaVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String title;
    private List<String> tags;
    private String createTime;
}
