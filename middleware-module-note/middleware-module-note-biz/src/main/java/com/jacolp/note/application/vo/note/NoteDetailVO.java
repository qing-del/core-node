package com.jacolp.note.application.vo.note;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import com.jacolp.note.application.vo.image.ImageSimpleVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class NoteDetailVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long userId;
    private Long topicId;
    private String topicName;
    private String title;
    private String description;
    private Integer storageType;
    private Short status;
    private Integer missingInfoMask;
    private Integer missingCount;
    private Long mdFileSize;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<String> tags;
    private List<ImageSimpleVO> images;
    private List<NoteEachSimpleVO> eachNotes;
    private NoteConvertResultVO converted;
}
