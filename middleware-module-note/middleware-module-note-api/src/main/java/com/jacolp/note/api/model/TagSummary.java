package com.jacolp.note.api.model;

/**
 * Minimal tag data permitted outside the note module for batch display and relation resolution.
 */
public record TagSummary(
        Long id,
        Long userId,
        String name,
        TagReviewStatus status
) {
}
