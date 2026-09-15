package com.jacolp.module.note.biz.application.vo.topic;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicListVO implements Serializable {

    private Long id;

    private Long userId;

    private String topicName;

    private Long parentId;

    private Integer sortOrder;

    private Long noteCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
