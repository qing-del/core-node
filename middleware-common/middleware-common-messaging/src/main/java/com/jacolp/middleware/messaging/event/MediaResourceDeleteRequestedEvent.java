package com.jacolp.middleware.messaging.event;

/** Requests physical deletion after the media database transaction has committed. */
public record MediaResourceDeleteRequestedEvent(String resourceId, String objectKey, long trackingId) {
    public MediaResourceDeleteRequestedEvent {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey must not be blank");
        }
        if (trackingId <= 0) throw new IllegalArgumentException("trackingId must be positive");
    }
}
