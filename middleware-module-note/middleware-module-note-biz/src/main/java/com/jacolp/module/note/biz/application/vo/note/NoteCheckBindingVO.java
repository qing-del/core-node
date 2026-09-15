package com.jacolp.module.note.biz.application.vo.note;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 笔记检查绑定结果 VO
 * <p>用于返回检查绑定时的状态信息和缺失资源列表</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteCheckBindingVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 笔记ID
     */
    private Long noteId;

    /**
     * 当前状态
     */
    private Short status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 是否信息完整
     */
    private boolean isComplete;

    /**
     * 缺失信息掩码
     */
    private Integer missingInfoMask;

    /**
     * 缺失信息数量
     */
    private Integer missingCount;

    /**
     * 缺失的标签列表
     */
    private List<String> missingTags;

    /**
     * 缺失的图片名称列表
     */
    private List<String> missingImages;

    /**
     * 缺失的内联笔记名称列表
     */
    private List<String> missingNoteNames;
}
