package com.jacolp.module.note.biz.application.dto.topic;

import java.io.Serializable;

import com.jacolp.constant.TopicConstant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicAddDTO implements Serializable {
    @NotBlank(message = "标题名字不能为空")
    @Size(max = TopicConstant.MAX_TOPIC_NAME_LENGTH, message = "标题名字长度超出限制")
    private String topicName;

    @Positive(message = "父级主题 ID 必须为正数")
    private Long parentId;

    @Positive(message = "排序序号必须为正数")
    private Integer sortOrder;
}
