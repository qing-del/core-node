package com.jacolp.common.security.oauth2.token;

import java.time.Instant;
import java.util.regex.Pattern;

/** Safe identity and expiry data needed to revoke one current OAuth2 session. */
public record OAuth2SessionRevocationRequest(
        long userId,
        String clientId,
        String accessJti,
        Instant accessExpiresAt,
        String refreshFingerprint) {

    private static final Pattern CLIENT = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final Pattern JTI = Pattern.compile("[A-Za-z0-9_-]{22}");
    private static final Pattern FINGERPRINT = Pattern.compile("[A-Za-z0-9_-]{43}");

    public OAuth2SessionRevocationRequest {
        if (userId <= 0 || clientId == null || !CLIENT.matcher(clientId).matches()
                || accessJti == null || !JTI.matcher(accessJti).matches() || accessExpiresAt == null
                || (refreshFingerprint != null && !FINGERPRINT.matcher(refreshFingerprint).matches())) {
            throw new IllegalArgumentException("Invalid OAuth2 session revocation request");
        }
    }

    @Override
    public String toString() {
        return "OAuth2SessionRevocationRequest[userId=" + userId + ", clientId=" + clientId
                + ", accessExpiresAt=" + accessExpiresAt + ", hasRefreshFingerprint="
                + (refreshFingerprint != null) + ']';
    }
}
