package com.jacolp.note.application.vo.note;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import com.jacolp.note.application.vo.image.ImageSimpleVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
@Schema(description = "公开笔记详情")
public class PublicNoteDetailVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long topicId;
    private String topicName;
    private String title;
    private String description;
    private List<String> tags;
    private List<ImageSimpleVO> images;
    private List<NoteEachSimpleVO> eachNotes;
    private NoteConvertResultVO converted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
