package com.jacolp.module.media.api.model;

/**
 * Minimal media data permitted outside the media module for batch display and relation resolution.
 */
public record MediaFileSummary(
        Long id,
        Long userId,
        Long topicId,
        String filename,
        String url,
        long sizeBytes,
        boolean publiclyVisible,
        MediaReviewStatus status
) {
}
