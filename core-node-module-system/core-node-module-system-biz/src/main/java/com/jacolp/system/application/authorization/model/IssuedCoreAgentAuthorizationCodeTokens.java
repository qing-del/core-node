package com.jacolp.system.application.authorization.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Raw CORE AGENT token response values. Diagnostic output deliberately never contains either token.
 */
public record IssuedCoreAgentAuthorizationCodeTokens(
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant accessIssuedAt,
        Instant accessExpiresAt,
        Instant refreshIssuedAt,
        Instant refreshExpiresAt,
        List<String> grantedScopes,
        boolean socketAddressChanged) {

    public IssuedCoreAgentAuthorizationCodeTokens {
        if (!hasText(accessToken) || !hasText(refreshToken) || !"Bearer".equals(tokenType)
                || accessIssuedAt == null || accessExpiresAt == null || refreshIssuedAt == null || refreshExpiresAt == null
                || !accessExpiresAt.isAfter(accessIssuedAt) || !refreshExpiresAt.isAfter(refreshIssuedAt)
                || refreshExpiresAt.isBefore(accessExpiresAt)) {
            throw new IllegalArgumentException("Invalid issued CORE AGENT authorization-code tokens");
        }
        if (grantedScopes == null || grantedScopes.isEmpty()) {
            throw new IllegalArgumentException("Invalid issued CORE AGENT authorization-code tokens");
        }
        List<String> normalizedScopes = new ArrayList<>(grantedScopes);
        normalizedScopes.sort(String::compareTo);
        for (int index = 0; index < normalizedScopes.size(); index++) {
            if (!hasText(normalizedScopes.get(index))
                    || (index > 0 && normalizedScopes.get(index - 1).equals(normalizedScopes.get(index)))) {
                throw new IllegalArgumentException("Invalid issued CORE AGENT authorization-code tokens");
            }
        }
        grantedScopes = List.copyOf(normalizedScopes);
    }

    @Override
    public String toString() {
        return "IssuedCoreAgentAuthorizationCodeTokens[accessToken=<redacted>, refreshToken=<redacted>, tokenType="
                + tokenType + ", accessExpiresAt=" + accessExpiresAt + ", refreshExpiresAt=" + refreshExpiresAt
                + ", scopes=" + grantedScopes.size() + ", socketAddressChanged=" + socketAddressChanged + ']';
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
