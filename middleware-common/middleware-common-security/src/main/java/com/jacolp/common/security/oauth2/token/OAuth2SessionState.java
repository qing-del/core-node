package com.jacolp.common.security.oauth2.token;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** Current client-user OAuth2 session references without raw credentials or claims. */
public record OAuth2SessionState(
        long userId,
        String clientId,
        String currentAccessJti,
        Instant accessExpiresAt,
        String currentRefreshFingerprint,
        Instant refreshExpiresAt) {
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final Pattern JTI = Pattern.compile("[A-Za-z0-9_-]{22}");
    private static final Pattern FINGERPRINT = Pattern.compile("[A-Za-z0-9_-]{43}");

    public OAuth2SessionState {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        if (clientId == null || !CLIENT_ID.matcher(clientId).matches()) throw new IllegalArgumentException("clientId must be safe");
        if (currentAccessJti == null || !JTI.matcher(currentAccessJti).matches()) throw new IllegalArgumentException("currentAccessJti must be Base64URL");
        if (currentRefreshFingerprint == null || !FINGERPRINT.matcher(currentRefreshFingerprint).matches()) throw new IllegalArgumentException("currentRefreshFingerprint must be Base64URL");
        accessExpiresAt = Objects.requireNonNull(accessExpiresAt, "accessExpiresAt must not be null");
        refreshExpiresAt = Objects.requireNonNull(refreshExpiresAt, "refreshExpiresAt must not be null");
    }
}
