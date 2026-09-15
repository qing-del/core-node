package com.jacolp.module.note.biz.application.dto.topic;

import java.io.Serializable;

import com.jacolp.module.note.biz.application.dto.PageParamProvider;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicListDTO implements Serializable, PageParamProvider {

    private static final long serialVersionUID = 1L;

    @Positive(message = "用户ID必须为正数")
    private Long userId;

    private String keyword;

    @Positive(message = "页码必须为正数")
    private Integer pageNum;

    @Positive(message = "页大小必须为正数")
    private Integer pageSize;

    private String sortBy;
}
