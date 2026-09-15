package com.jacolp.module.note.biz.application.vo.tag;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagVO implements Serializable {

    private Long id;

    private Long userId;

    private String tagName;

    private Short auditStatus;

    private LocalDateTime createTime;
}
