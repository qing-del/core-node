package com.jacolp.note.infrastructure.persistence.dataobject;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记与标签多对多关联表 biz_note_tag_mapping 对应实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteTagMappingDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long noteId;

    private Long tagId;

    private String parsedTagName;

    private Short status;

    private Short isDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
