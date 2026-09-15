package com.jacolp.module.note.biz.application.dto.note;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadToInsertNoteDTO {
    Long fileSize;
    Long topicId;
    Long userId;
    String originalFilename;
    List<String> tags;
    List<String> imageNames;
    List<String> noteNames;
}
