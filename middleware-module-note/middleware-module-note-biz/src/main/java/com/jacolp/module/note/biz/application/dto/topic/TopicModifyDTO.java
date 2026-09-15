package com.jacolp.module.note.biz.application.dto.topic;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicModifyDTO implements Serializable {

    @NotBlank(message = "标题 ID 不能为空")
    @Positive(message = "标题 ID 必须为正数")
    private Long id;

    @Positive(message = "父级主题 ID 必须为正数")
    private Long parentId;

    @Positive(message = "排序序号必须为正数")
    private Integer sortOrder;
}
