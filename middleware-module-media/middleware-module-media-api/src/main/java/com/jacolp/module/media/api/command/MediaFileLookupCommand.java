package com.jacolp.module.media.api.command;

import java.util.List;
import java.util.Objects;

/**
 * Batch lookup of media files by their owner, topic and filename.
 */
public record MediaFileLookupCommand(Long userId, Long topicId, List<String> filenames) {

    public MediaFileLookupCommand {
        userId = Objects.requireNonNull(userId, "userId");
        filenames = List.copyOf(Objects.requireNonNull(filenames, "filenames"));
    }
}
