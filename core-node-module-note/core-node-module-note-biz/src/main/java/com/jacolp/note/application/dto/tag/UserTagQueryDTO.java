package com.jacolp.note.application.dto.tag;

import java.io.Serializable;

import com.jacolp.note.application.dto.PageParamProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户端标签条件查询 DTO。
 * 查询逻辑：当前用户自己的标签 + 别人已通过审核的标签。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserTagQueryDTO implements Serializable, PageParamProvider {

    private static final long serialVersionUID = 1L;

    private String keyword;

    /**
     * 查询范围：{@code "global"} 时包含他人已通过审核标签，其他值仅查当前用户标签。
     */
    private String scope;

    private Integer pageNum;

    private Integer pageSize;
}
