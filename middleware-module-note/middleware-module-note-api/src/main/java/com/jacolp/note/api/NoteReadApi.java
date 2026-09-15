package com.jacolp.note.api;

import com.jacolp.note.api.model.NoteMediaReferenceSummary;
import com.jacolp.note.api.model.NoteSummary;
import com.jacolp.note.api.model.TagSummary;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Batch read contract for data owned by the note module.
 */
public interface NoteReadApi {

    /**
     * Returns note summaries keyed by note ID. Missing or deleted notes are omitted.
     */
    Map<Long, NoteSummary> findNoteSummariesByIds(Collection<Long> noteIds);

    /**
     * Returns tag summaries keyed by tag ID. Missing or deleted tags are omitted.
     */
    Map<Long, TagSummary> findTagSummariesByIds(Collection<Long> tagIds);

    /**
     * Returns the active note-reference count for every requested media ID.
     * IDs with no references must be present with a count of {@code 0}.
     */
    Map<Long, Long> countMediaReferencesByMediaIds(Collection<Long> mediaIds);

    /** Returns associated note summaries for every requested media ID using batch queries. */
    Map<Long, List<NoteSummary>> findNoteSummariesByMediaIds(Collection<Long> mediaIds);

    /** Compatibility projection for the legacy image-to-note list endpoint. */
    Map<Long, List<NoteMediaReferenceSummary>> findNoteMediaReferenceSummariesByMediaIds(Collection<Long> mediaIds);

    /**
     * Returns the current user's non-deleted markdown storage usage in bytes.
     */
    long getUserStorageUsageBytes(Long userId);
}
