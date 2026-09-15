package com.jacolp.common.security.oauth2.token;

import java.time.Instant;
import java.util.regex.Pattern;

/** The encoded access token together with its non-secret issue metadata. */
public record IssuedAccessToken(String tokenValue, String jti, Instant issuedAt, Instant expiresAt) {

    private static final Pattern JTI = Pattern.compile("[A-Za-z0-9_-]{22}");

    public IssuedAccessToken {
        if (tokenValue == null || tokenValue.isBlank() || jti == null || !JTI.matcher(jti).matches()
                || issuedAt == null || expiresAt == null || !expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("Invalid issued access token");
        }
    }

    @Override
    public String toString() {
        return "IssuedAccessToken[tokenValue=<redacted>, jti=" + jti + ", issuedAt=" + issuedAt
                + ", expiresAt=" + expiresAt + ']';
    }
}
