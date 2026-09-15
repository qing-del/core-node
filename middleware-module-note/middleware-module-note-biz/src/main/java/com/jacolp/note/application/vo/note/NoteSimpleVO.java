package com.jacolp.note.application.vo.note;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class NoteSimpleVO implements Serializable {

    private Long id;
    private String title;
    private Short isCrossUser;
    private Short status;
    private LocalDateTime createTime;
}
