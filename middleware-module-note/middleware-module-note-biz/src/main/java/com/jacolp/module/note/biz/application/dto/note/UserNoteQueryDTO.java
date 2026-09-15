package com.jacolp.module.note.biz.application.dto.note;

import java.io.Serializable;

import com.jacolp.module.note.biz.application.dto.PageParamProvider;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户端笔记条件查询 DTO。
 * 查询逻辑：当前用户自己的笔记 + 别人已发布的笔记。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserNoteQueryDTO implements Serializable, PageParamProvider {

    private static final long serialVersionUID = 1L;

    private Long topicId;

    private Boolean unclassified;

    private String title;

    /**
     * 查询范围：{@code "global"} 时包含他人公开/已通过资源，其他值（包括 {@code null}、{@code "personal"}）仅查当前用户资源。
     */
    private String scope;

    private Integer pageNum;

    private Integer pageSize;
}
