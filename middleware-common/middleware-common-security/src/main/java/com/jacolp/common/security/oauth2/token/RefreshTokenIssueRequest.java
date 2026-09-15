package com.jacolp.common.security.oauth2.token;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Inputs needed to create refresh state; user profile and role data are deliberately absent. */
public record RefreshTokenIssueRequest(
        long userId,
        String clientId,
        List<String> grantedScopes,
        AccessTokenSessionReference accessToken,
        Duration refreshTtl) {
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    public RefreshTokenIssueRequest {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        if (clientId == null || !CLIENT_ID.matcher(clientId).matches()) throw new IllegalArgumentException("clientId must be safe");
        grantedScopes = List.copyOf(Objects.requireNonNull(grantedScopes, "grantedScopes must not be null"));
        accessToken = Objects.requireNonNull(accessToken, "accessToken must not be null");
        refreshTtl = Objects.requireNonNull(refreshTtl, "refreshTtl must not be null");
        if (refreshTtl.isZero() || refreshTtl.isNegative()) throw new IllegalArgumentException("refreshTtl must be positive");
    }
}
