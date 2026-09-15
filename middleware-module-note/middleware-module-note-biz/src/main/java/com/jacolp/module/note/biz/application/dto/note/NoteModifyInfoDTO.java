package com.jacolp.module.note.biz.application.dto.note;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "更新笔记内容请求")
public class NoteModifyInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "笔记ID", example = "1", required = true)
    private Long id;

    @Schema(description = "主题ID", example = "1")
    private Long topicId;

    @Schema(description = "笔记描述", example = "更新后的描述")
    private String description;
}
