package com.jacolp.module.note.api.model;

/**
 * Minimal note data permitted outside the note module for batch display and relation resolution.
 */
public record NoteSummary(
        Long id,
        Long userId,
        Long topicId,
        String title,
        NoteLifecycleStatus status,
        long storageBytes
) {

    public NoteSummary(Long id, Long userId, Long topicId, String title, NoteLifecycleStatus status) {
        this(id, userId, topicId, title, status, 0L);
    }
}
