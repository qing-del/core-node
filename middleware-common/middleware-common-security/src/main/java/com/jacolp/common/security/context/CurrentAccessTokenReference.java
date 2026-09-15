package com.jacolp.common.security.context;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** Non-secret reference to the current RS256 access token. */
public record CurrentAccessTokenReference(long userId, String clientId, String jti, Instant expiresAt) {
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final Pattern JTI = Pattern.compile("[A-Za-z0-9_-]{22}");

    public CurrentAccessTokenReference {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (clientId == null || !CLIENT_ID.matcher(clientId).matches()) {
            throw new IllegalArgumentException("clientId must be safe");
        }
        if (jti == null || !JTI.matcher(jti).matches()) {
            throw new IllegalArgumentException("jti must be Base64URL");
        }
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
