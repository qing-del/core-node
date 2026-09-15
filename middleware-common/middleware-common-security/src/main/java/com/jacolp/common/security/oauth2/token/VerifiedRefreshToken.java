package com.jacolp.common.security.oauth2.token;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Minimal verified refresh identity; callers must reload authorization data for every refresh grant. */
public record VerifiedRefreshToken(
        String fingerprint,
        long userId,
        String clientId,
        List<String> grantedScopes,
        Instant expiresAt) {
    private static final Pattern FINGERPRINT = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    public VerifiedRefreshToken {
        if (fingerprint == null || !FINGERPRINT.matcher(fingerprint).matches()) throw new IllegalArgumentException("fingerprint must be Base64URL");
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        if (clientId == null || !CLIENT_ID.matcher(clientId).matches()) throw new IllegalArgumentException("clientId must be safe");
        grantedScopes = List.copyOf(Objects.requireNonNull(grantedScopes, "grantedScopes must not be null"));
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
