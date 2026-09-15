package com.jacolp.module.note.biz.application.vo.note;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long topicId;
    private String topicName;
    private String title;
    private String description;
    private Integer storageType;
    private Short status;
    private Short isChanging;
    private Integer missingInfoMask;
    private Integer missingCount;
    private Long mdFileSize;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
