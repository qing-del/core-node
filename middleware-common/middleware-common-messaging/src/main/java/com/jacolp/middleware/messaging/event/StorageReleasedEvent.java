package com.jacolp.middleware.messaging.event;

/** Storage release is a business fact emitted only after the owning resource is deleted. */
public record StorageReleasedEvent(long userId, String resourceType, String resourceId, long releasedBytes) {
    public StorageReleasedEvent {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("resourceType must not be blank");
        }
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        if (releasedBytes <= 0) throw new IllegalArgumentException("releasedBytes must be positive");
    }
}
