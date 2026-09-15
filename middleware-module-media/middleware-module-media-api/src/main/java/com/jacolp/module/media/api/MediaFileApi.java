package com.jacolp.module.media.api;

import com.jacolp.module.media.api.command.MediaFileLookupCommand;
import com.jacolp.module.media.api.model.MediaFileSummary;

import java.util.Collection;
import java.util.Map;

/**
 * Batch read contract for media files. It deliberately exposes no media entity or mapper type.
 */
public interface MediaFileApi {

    /**
     * Returns media summaries keyed by ID. Missing or deleted media files are omitted.
     */
    Map<Long, MediaFileSummary> findByIds(Collection<Long> mediaIds);

    /**
     * Finds files by one owner/topic scope and a batch of filenames.
     */
    Map<String, MediaFileSummary> findByOwnerTopicAndFilenames(MediaFileLookupCommand command);
}
