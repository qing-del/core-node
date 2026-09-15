package com.jacolp.module.note.api.model;

/**
 * Counts the tag rows and note-to-tag relation rows changed by one audit decision.
 */
public record TagAuditApplyResult(int tagRowsUpdated, int relationRowsUpdated) {
}
