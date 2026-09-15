package com.jacolp.module.note.api.model;

/**
 * Public read model of a note's lifecycle, independent of legacy database codes.
 */
public enum NoteLifecycleStatus {
    NEW,
    PENDING_INFO,
    READY_TO_CONVERT,
    CONVERTED,
    PENDING_AUDIT,
    APPROVED,
    PUBLISHED,
    REJECTED,
    DELETED
}
