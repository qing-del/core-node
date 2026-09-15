package com.jacolp.system.application.authorization.model;

import java.time.Instant;
import java.util.regex.Pattern;

/** Validated email-code challenge state, excluding raw code and email. */
public record EmailLoginCodeState(
        String clientId,
        Long userId,
        String emailFingerprint,
        String verifierHash,
        Integer failedAttempts,
        Instant issuedAt,
        Instant expiresAt) {
    private static final Pattern FINGERPRINT = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]?\\$[0-9]{2}\\$[./A-Za-z0-9]{53}");
    public EmailLoginCodeState {
        if (!"user".equals(clientId) && !"admin".equals(clientId)) {
            throw new IllegalArgumentException("Invalid email-code state");
        }
        if (userId == null || userId <= 0 || emailFingerprint == null || !FINGERPRINT.matcher(emailFingerprint).matches()
                || verifierHash == null || !BCRYPT.matcher(verifierHash).matches() || failedAttempts == null
                || failedAttempts < 0 || failedAttempts > 4 || issuedAt == null || expiresAt == null || !expiresAt.isAfter(issuedAt))
        {
            throw new IllegalArgumentException("Invalid email-code state");
        }
    }
    @Override
    public String toString() {
        return "EmailLoginCodeState[clientId=" + clientId + ", userId=" + userId
                + ", failedAttempts=" + failedAttempts + ", issuedAt=" + issuedAt + ", expiresAt=" + expiresAt + ']';
    }
}
