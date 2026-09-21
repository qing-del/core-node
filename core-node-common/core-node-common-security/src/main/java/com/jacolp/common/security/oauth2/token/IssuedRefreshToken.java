package com.jacolp.common.security.oauth2.token;

import java.time.Instant;
import java.util.Objects;

/** Delivers a raw refresh token only to the caller; logging is deliberately redacted. */
public record IssuedRefreshToken(String rawToken, Instant issuedAt, Instant expiresAt) {
    public IssuedRefreshToken {
        rawToken = Objects.requireNonNull(rawToken, "rawToken must not be null");
        if (rawToken.isBlank()) {
            throw new IllegalArgumentException("rawToken must not be blank");
        }
        issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }

    @Override
    public String toString() {
        return "IssuedRefreshToken[rawToken=<redacted>, issuedAt=" + issuedAt + ", expiresAt=" + expiresAt + "]";
    }
}
