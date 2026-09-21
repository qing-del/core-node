package com.jacolp.note.application.vo.note;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标签反向引用笔记 VO（哪些笔记引用了当前标签）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagBacklinkVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "源笔记 ID")
    private Long sourceNoteId;

    @Schema(description = "源笔记标题")
    private String sourceNoteTitle;

    @Schema(description = "源笔记中解析出的标签名称")
    private String parsedTagName;

    @Schema(description = "是否跨用户")
    private Short isCrossUser;

    @Schema(description = "源笔记状态")
    private Short sourceNoteStatus;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
