package com.jacolp.module.media.api;

/**
 * Reads storage usage owned by the media module.
 */
public interface MediaUsageApi {

    /**
     * Returns the current user's non-deleted media storage usage in bytes.
     */
    long getUserStorageUsageBytes(Long userId);
}
