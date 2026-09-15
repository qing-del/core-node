package com.jacolp.note.application.dto.note;

import java.io.Serializable;

import com.jacolp.note.application.dto.PageParamProvider;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "公开笔记查询请求")
public class PublicNoteQueryDTO implements Serializable, PageParamProvider {
    private static final long serialVersionUID = 1L;
    @Schema(description = "搜索关键词（标题模糊搜索）", example = "Java")
    private String keyword;
    @Schema(description = "主题ID", example = "1")
    private Long topicId;
    @Schema(description = "页码（默认1）", example = "1")
    private Integer pageNum;
    @Schema(description = "每页大小（默认15）", example = "15")
    private Integer pageSize;
}
