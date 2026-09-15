package com.jacolp.middleware.messaging.event;

public record UserProfileChangedEvent(long userId, String username, String nickname) {
    public UserProfileChangedEvent {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        if (username == null || username.isBlank()) throw new IllegalArgumentException("username is required");
    }
}
