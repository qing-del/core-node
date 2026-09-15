package com.jacolp.note.infrastructure.persistence.dataobject;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteConvertedDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long noteId;

    private String title;

    private String tagsJson;

    private String createTimeStr;

    private String tocHtml;

    private String bodyHtml;

    private LocalDateTime convertTime;
}
