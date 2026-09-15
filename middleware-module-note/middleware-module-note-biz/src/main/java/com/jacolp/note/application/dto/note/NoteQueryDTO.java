package com.jacolp.note.application.dto.note;

import java.io.Serializable;

import com.jacolp.note.application.dto.PageParamProvider;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteQueryDTO implements Serializable, PageParamProvider {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private Long topicId;

    private Boolean unclassified;

    private String title;

    private Short status;  // 笔记状态过滤

    private Integer pageNum;

    private Integer pageSize;
}
