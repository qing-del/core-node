package com.jacolp.system.application.dto.authorization;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jacolp.system.application.authorization.model.InternalLoginRequest;

import java.util.LinkedHashSet;
import java.util.Set;

/** HTTP representation of an internal USER/ADMIN login request. */
public record InternalLoginHttpRequest(
        @JsonProperty("client_id") String clientId,
        @JsonProperty("grant_type") String grantType,
        @JsonProperty("username") String username,
        @JsonProperty("password") String password,
        @JsonProperty("email") String email,
        @JsonProperty("code") String code,
        @JsonProperty("scope") String scope,
        @JsonProperty("refresh_token") String refreshToken) {

    public InternalLoginHttpRequest(String clientId, String grantType, String username, String password,
                                    String email, String code, String scope) {
        this(clientId, grantType, username, password, email, code, scope, null);
    }

    public InternalLoginRequest toDomain(String socketRemoteAddress) {
        return new InternalLoginRequest(
                clientId,
                grantType,
                username,
                password,
                email,
                code,
                parseScopes(scope),
                socketRemoteAddress,
                refreshToken);
    }

    @Override
    public String toString() {
        return "InternalLoginHttpRequest[clientId=" + clientId + ", grantType=" + grantType
                + ", scope=" + (scope == null ? "omitted" : scopeCount(scope)) + ']';
    }

    private static Set<String> parseScopes(String scope) {
        if (scope == null) {
            return null;
        }
        if (scope.isEmpty()) {
            return Set.of();
        }
        if (scope.charAt(0) == ' ' || scope.charAt(scope.length() - 1) == ' ') {
            throw new IllegalArgumentException("Invalid internal login scope");
        }

        LinkedHashSet<String> scopes = new LinkedHashSet<>();
        int start = 0;
        for (int index = 0; index < scope.length(); index++) {
            char character = scope.charAt(index);
            if ((Character.isWhitespace(character) || Character.isSpaceChar(character)) && character != ' ') {
                throw new IllegalArgumentException("Invalid internal login scope");
            }
            if (character == ' ') {
                if (index == start || !scopes.add(scope.substring(start, index))) {
                    throw new IllegalArgumentException("Invalid internal login scope");
                }
                start = index + 1;
            }
        }
        if (!scopes.add(scope.substring(start))) {
            throw new IllegalArgumentException("Invalid internal login scope");
        }
        return Set.copyOf(scopes);
    }

    private static int scopeCount(String scope) {
        return scope.isEmpty() ? 0 : parseScopes(scope).size();
    }
}
