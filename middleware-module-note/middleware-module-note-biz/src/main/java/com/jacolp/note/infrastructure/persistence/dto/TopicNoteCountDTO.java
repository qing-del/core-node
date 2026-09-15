package com.jacolp.note.infrastructure.persistence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicNoteCountDTO {
    private String topicName;
    private Long noteCount;
    private Long userId;
}
