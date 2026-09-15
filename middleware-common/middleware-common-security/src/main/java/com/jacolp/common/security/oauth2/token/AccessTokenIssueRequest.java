package com.jacolp.common.security.oauth2.token;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Request data for a single-role RS256 access token. */
public record AccessTokenIssueRequest(long userId, String clientId, String grantType, String username,
                                      String role, Set<String> scopes, Duration tokenTtl) {

    public AccessTokenIssueRequest {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        clientId = requiredIdentifier(clientId, "clientId");
        grantType = requiredIdentifier(grantType, "grantType");
        username = requiredIdentifier(username, "username");
        role = requiredIdentifier(role, "role");
        if (tokenTtl == null || tokenTtl.isZero() || tokenTtl.isNegative()) {
            throw new IllegalArgumentException("tokenTtl must be positive");
        }
        Set<String> copiedScopes = new LinkedHashSet<>();
        for (String scope : Objects.requireNonNull(scopes, "scopes must not be null")) {
            copiedScopes.add(requiredIdentifier(scope, "scope"));
        }
        scopes = Collections.unmodifiableSet(copiedScopes);
    }

    private static String requiredIdentifier(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
