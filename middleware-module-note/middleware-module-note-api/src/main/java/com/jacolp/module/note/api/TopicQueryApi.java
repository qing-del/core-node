package com.jacolp.module.note.api;

/**
 * Reads topic ownership without relying on an ambient authentication context.
 */
public interface TopicQueryApi {

    /**
     * Returns whether the topic exists and belongs to the supplied user.
     */
    boolean isOwnedBy(Long topicId, Long userId);
}
