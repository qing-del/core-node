package com.jacolp.module.note.biz.infrastructure.persistence.dataobject;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记标签表 biz_tag 对应实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private String tagName;

    private Short auditStatus;

    private LocalDateTime createTime;
}
