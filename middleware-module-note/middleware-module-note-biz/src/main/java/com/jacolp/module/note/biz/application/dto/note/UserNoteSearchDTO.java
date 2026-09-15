package com.jacolp.module.note.biz.application.dto.note;

import com.jacolp.module.note.biz.application.dto.PageParamProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户端笔记搜索 DTO
 */
@Data
@Schema(description = "笔记搜索/查询请求")
public class UserNoteSearchDTO implements Serializable, PageParamProvider {

    private static final long serialVersionUID = 1L;

    @Schema(description = "搜索关键词（支持标题模糊搜索）", example = "Java")
    private String keyword;

    @Schema(description = "主题ID", example = "1")
    private Long topicId;

    @Schema(description = "是否只查询未归类笔记(topicId为空)", example = "false")
    private Boolean unclassified;

    @Schema(description = "标签ID", example = "1")
    private Long tagId;

    @Schema(description = "查询范围：global=全局，其他值=仅当前用户", example = "personal")
    private String scope;

    @Schema(description = "页码（默认1）", example = "1")
    private Integer pageNum;

    @Schema(description = "每页大小（默认10）", example = "10")
    private Integer pageSize;
}
