package com.jacolp.note.infrastructure.persistence.dataobject;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记-图片引用映射表 biz_note_image_mapping 对应实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteImageMappingDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long noteId;

    private Long imageId;

    private Long noteUserId;  // 笔记所属用户ID

    private Long imageUserId;  // 图片所属用户ID

    private String parsedImageName;  // 笔记中原始图片名称

    private String noteTitle;  // 笔记标题

    private Short isCrossUser;  // 是否跨用户引用（0:否, 1:是）

    private Short status;  // 映射状态快照

    private Short isDeleted;  // 是否删除（0:正常, 1:已删除）

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
