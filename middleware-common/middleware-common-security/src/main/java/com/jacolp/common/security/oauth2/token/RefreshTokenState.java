package com.jacolp.common.security.oauth2.token;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Persistable refresh authorization state; user profile and role are intentionally not cached. */
public record RefreshTokenState(
        String fingerprint,
        String verifierHash,
        long userId,
        String clientId,
        List<String> grantedScopes,
        Instant issuedAt,
        Instant expiresAt) {
    private static final Pattern FINGERPRINT = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}");
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    public RefreshTokenState {
        if (fingerprint == null || !FINGERPRINT.matcher(fingerprint).matches()) throw new IllegalArgumentException("fingerprint must be Base64URL SHA-256");
        if (verifierHash == null || !BCRYPT.matcher(verifierHash).matches()) throw new IllegalArgumentException("verifierHash must be BCrypt");
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        if (clientId == null || !CLIENT_ID.matcher(clientId).matches()) throw new IllegalArgumentException("clientId must be safe");
        grantedScopes = Objects.requireNonNull(grantedScopes, "grantedScopes must not be null").stream()
                .map(scope -> { if (scope == null || scope.isBlank()) throw new IllegalArgumentException("scope must not be blank"); return scope; })
                .distinct().sorted().toList();
        issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!issuedAt.isBefore(expiresAt)) throw new IllegalArgumentException("issuedAt must precede expiresAt");
    }

    @Override public String toString() { return "RefreshTokenState[fingerprint=" + fingerprint + ", userId=" + userId + ", clientId=" + clientId + "]"; }
}
