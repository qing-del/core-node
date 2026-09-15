package com.jacolp.system.application.dto.authorization;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jacolp.system.application.authorization.model.InternalIssuedTokens;

import java.time.Duration;
import java.util.Objects;

/** HTTP representation of internally issued access and refresh tokens. */
public record InternalTokenHttpResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("scope") String scope) {

    public static InternalTokenHttpResponse from(InternalIssuedTokens tokens) {
        Objects.requireNonNull(tokens, "tokens");
        long expiresIn = Duration.between(tokens.accessIssuedAt(), tokens.accessExpiresAt()).toSeconds();
        if (expiresIn <= 0) {
            throw new IllegalArgumentException("Access token expiry must be at least one second");
        }
        return new InternalTokenHttpResponse(
                tokens.accessToken(),
                tokens.tokenType(),
                expiresIn,
                tokens.refreshToken(),
                String.join(" ", tokens.grantedScopes()));
    }

    @Override
    public String toString() {
        return "InternalTokenHttpResponse[accessToken=<redacted>, tokenType=" + tokenType
                + ", expiresIn=" + expiresIn + ", refreshToken=<redacted>, scopeCount="
                + scopeCount(scope) + ']';
    }

    private static int scopeCount(String scope) {
        return scope.isEmpty() ? 0 : scope.split(" ", -1).length;
    }
}
