package com.jacolp.note.api.model;

/** 笔记 file 索引所需的跨模块最小数据。 */
public record NoteFileIndexSource(Long id, Long ownerUserId, String fileName, boolean publicResource,
                                  boolean deleted) {

    /** 校验索引源的身份和名称字段。 */
    public NoteFileIndexSource {
        if (id == null || id <= 0 || ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("note index source IDs must be positive");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("note index source fileName must not be blank");
        }
    }
}
