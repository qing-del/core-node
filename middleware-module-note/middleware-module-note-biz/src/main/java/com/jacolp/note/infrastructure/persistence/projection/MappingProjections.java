package com.jacolp.note.infrastructure.persistence.projection;

import java.time.LocalDateTime;

import lombok.Data;

public final class MappingProjections {
    private MappingProjections() {}

    @Data public static class ImageNoteCount { private Long imageId; private String filename; private Integer refCount; }
    @Data public static class ImageSimple { private Long imageId; private Long noteId; private String parsedImageName; private String filename; private String ossUrl; private Short isPublic; private Short status; private Short isCrossUser; private Short isMissing; private LocalDateTime createTime; }
    @Data public static class NoteEachSimple { private Long targetNoteId; private String targetNoteTitle; private String parsedNoteName; private String anchor; private String nickname; private Short isMissing; }
    @Data public static class NoteBacklink { private Long sourceNoteId; private String sourceNoteTitle; private String parsedNoteName; private String anchor; private String nickname; private Short isCrossUser; private Short sourceNoteStatus; private LocalDateTime createTime; }
    @Data public static class TagBacklink { private Long sourceNoteId; private String sourceNoteTitle; private String parsedTagName; private Short isCrossUser; private Short sourceNoteStatus; private LocalDateTime createTime; }
    @Data public static class ImageBacklink { private Long sourceNoteId; private String sourceNoteTitle; private String parsedImageName; private Short isCrossUser; private Short sourceNoteStatus; private LocalDateTime createTime; }
}
