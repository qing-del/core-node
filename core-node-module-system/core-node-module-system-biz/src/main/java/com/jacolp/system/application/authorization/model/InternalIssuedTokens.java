package com.jacolp.system.application.authorization.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Raw internal tokens with redacted diagnostics. */
public record InternalIssuedTokens(
        String accessToken, String refreshToken, String tokenType, Instant accessIssuedAt,
        Instant accessExpiresAt, Instant refreshExpiresAt, List<String> grantedScopes) {
    public InternalIssuedTokens {
        if (!text(accessToken) || !text(refreshToken) || !"Bearer".equals(tokenType) || accessIssuedAt == null
                || accessExpiresAt == null || refreshExpiresAt == null || !accessExpiresAt.isAfter(accessIssuedAt)
                || refreshExpiresAt.isBefore(accessExpiresAt)) {
            throw new IllegalArgumentException("Invalid issued tokens");
        }
        if (grantedScopes == null) {
            throw new IllegalArgumentException("Invalid issued tokens");
        }
        List<String> scopes = new ArrayList<>(grantedScopes);
        if (scopes.stream().anyMatch(scope -> !text(scope))) {
            throw new IllegalArgumentException("Invalid issued tokens");
        }
        scopes.sort(String::compareTo);
        for (int i = 1; i < scopes.size(); i++) {
            if (scopes.get(i - 1).equals(scopes.get(i))) {
                throw new IllegalArgumentException("Invalid issued tokens");
            }
        }
        grantedScopes = List.copyOf(scopes);
    }
    @Override
    public String toString() {
        return "InternalIssuedTokens[accessToken=<redacted>, refreshToken=<redacted>, scopes="
                + grantedScopes.size() + "]";
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }
}
