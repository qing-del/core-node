package com.jacolp.note.application.vo.note;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class NoteConvertResultVO implements Serializable {
    private static final long serialVersionUID = 1L;
    @Schema(description = "标题、标签、创建时间")
    private NoteConvertMetaVO meta;
    @Schema(description = "目录 HTML - (可以用于创建标题跳转栏)")
    private String tocHtml;
    @Schema(description = "正文 HTML")
    private String bodyHtml;
}
