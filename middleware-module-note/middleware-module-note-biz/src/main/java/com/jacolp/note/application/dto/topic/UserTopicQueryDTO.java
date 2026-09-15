package com.jacolp.note.application.dto.topic;

import java.io.Serializable;

import com.jacolp.note.application.dto.PageParamProvider;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户端主题条件查询 DTO。
 * 查询逻辑：当前用户自己的主题 + 别人已通过审核的主题。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserTopicQueryDTO implements Serializable, PageParamProvider {

    private static final long serialVersionUID = 1L;

    private String keyword;

    /**
     * 查询范围：{@code "global"} 时包含他人已通过审核主题，其他值仅查当前用户主题。
     */
    private String scope;

    @Positive(message = "父级主题 ID 必须为正数")
    private Long parentId;

    @Positive(message = "页码必须为正数")
    private Integer pageNum;

    @Positive(message = "每页数量必须为正数")
    private Integer pageSize;
}
