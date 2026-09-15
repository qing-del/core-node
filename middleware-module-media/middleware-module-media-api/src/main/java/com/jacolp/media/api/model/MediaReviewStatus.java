package com.jacolp.media.api.model;

/**
 * Review state of a media file, expressed without legacy numeric status values.
 */
public enum MediaReviewStatus {
    WAITING,
    REVIEWING,
    APPROVED,
    REJECTED,
    DELETED
}
