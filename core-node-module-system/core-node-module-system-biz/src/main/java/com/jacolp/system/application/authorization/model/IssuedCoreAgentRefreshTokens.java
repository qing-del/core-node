package com.jacolp.system.application.authorization.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Raw CORE AGENT refresh-grant response values with redacted diagnostics. */
public record IssuedCoreAgentRefreshTokens(
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant accessIssuedAt,
        Instant accessExpiresAt,
        Instant refreshIssuedAt,
        Instant refreshExpiresAt,
        List<String> grantedScopes) {

    public IssuedCoreAgentRefreshTokens {
        if (!text(accessToken) || !text(refreshToken) || !"Bearer".equals(tokenType)
                || accessIssuedAt == null || accessExpiresAt == null || refreshIssuedAt == null || refreshExpiresAt == null
                || !accessExpiresAt.isAfter(accessIssuedAt) || !refreshExpiresAt.isAfter(refreshIssuedAt)
                || refreshExpiresAt.isBefore(accessExpiresAt) || grantedScopes == null || grantedScopes.isEmpty()) {
            throw new IllegalArgumentException("Invalid issued CORE AGENT refresh tokens");
        }
        List<String> scopes = new ArrayList<>(grantedScopes);
        scopes.sort(String::compareTo);
        for (int index = 0; index < scopes.size(); index++) {
            if (!text(scopes.get(index)) || (index > 0 && scopes.get(index - 1).equals(scopes.get(index)))) {
                throw new IllegalArgumentException("Invalid issued CORE AGENT refresh tokens");
            }
        }
        grantedScopes = List.copyOf(scopes);
    }

    @Override
    public String toString() {
        return "IssuedCoreAgentRefreshTokens[accessToken=<redacted>, refreshToken=<redacted>, tokenType=" + tokenType
                + ", accessExpiresAt=" + accessExpiresAt + ", refreshExpiresAt=" + refreshExpiresAt
                + ", scopes=" + grantedScopes.size() + ']';
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }
}
