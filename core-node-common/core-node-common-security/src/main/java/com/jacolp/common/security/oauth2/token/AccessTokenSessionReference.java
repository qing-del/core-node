package com.jacolp.common.security.oauth2.token;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** Non-sensitive access-token reference retained by the refresh-session state. */
public record AccessTokenSessionReference(String jti, Instant expiresAt) {
    private static final Pattern JTI = Pattern.compile("[A-Za-z0-9_-]{22}");

    public AccessTokenSessionReference {
        if (jti == null || !JTI.matcher(jti).matches()) {
            throw new IllegalArgumentException("jti must be Base64URL");
        }
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
