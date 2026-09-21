package com.jacolp.note.infrastructure.persistence.dataobject;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteChangeDiffDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long noteId;

    private Integer status;

    private String diffJson;

    private String scanJson;  // 新文本扫描结果保存为JSON(NoteReletionInfo)

    private Long oldFileSize;

    private Long newFileSize;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
