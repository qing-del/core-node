package com.jacolp.note.infrastructure.persistence.dataobject;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 笔记内容表实体
 * 与笔记记录一对一关联，存储笔记的 Markdown 原文内容
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteContextDO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 关联笔记ID (biz_note.id, 唯一)
     */
    private Long noteId;

    /**
     * Markdown 原始内容
     */
    private String markdownContent;

    /**
     * Markdown 新版本内容（修改上传时临时存储，待确认后覆盖旧版本）
     */
    private String markdownContentNew;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
