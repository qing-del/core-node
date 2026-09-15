package com.jacolp.note.infrastructure.persistence.dataobject;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记双链映射表实体。
 * 记录源笔记解析到的双链目标笔记。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteEachMappingDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long sourceNoteId;

    private Long targetNoteId;

    private String parsedNoteName;

    /**
     * 笔记锚点：双链中 {@code #} 之后的片段标题，如 {@code [[note.md#标题]]} 中的 {@code "标题"}。
     * 当双链语法中无 {@code #} 时为 null。
     */
    private String anchor;

    /**
     * 笔记别名：双链中 {@code |} 之后的自定义显示名，如 {@code [[note.md|别名]]} 中的 {@code "别名"}。
     * 当双链语法中无 {@code |} 时为 null。
     */
    private String nickname;

    private Short status;

    private Short isDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getNoteId() {
        return sourceNoteId;
    }
}
