package com.jacolp.note.api.model;

/**
 * Review state of a tag, expressed without legacy numeric status values.
 */
public enum TagReviewStatus {
    WAITING,
    REVIEWING,
    APPROVED,
    REJECTED,
    DELETED
}
