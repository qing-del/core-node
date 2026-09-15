package com.jacolp.module.media.api.model;

/**
 * Counts the media rows and note-image relation rows changed by one audit decision.
 */
public record MediaAuditApplyResult(int mediaRowsUpdated, int relationRowsUpdated) {
}
