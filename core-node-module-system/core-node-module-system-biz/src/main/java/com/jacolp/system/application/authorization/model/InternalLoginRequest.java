package com.jacolp.system.application.authorization.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict, redacted request model for USER/ADMIN internal login or refresh. */
public record InternalLoginRequest(
        String clientId, String grantType, String username, String rawPassword,
        String email, String rawEmailCode, Set<String> requestedScopes, String socketRemoteAddress,
        String rawRefreshToken) {

    private static final Pattern OPAQUE_TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    public InternalLoginRequest(String clientId, String grantType, String username, String rawPassword,
                                String email, String rawEmailCode, Set<String> requestedScopes,
                                String socketRemoteAddress) {
        this(clientId, grantType, username, rawPassword, email, rawEmailCode, requestedScopes,
                socketRemoteAddress, null);
    }

    public InternalLoginRequest {
        if ((!"user".equals(clientId) && !"admin".equals(clientId))
                || !("password".equals(grantType) || "email-code".equals(grantType)
                || "refresh_token".equals(grantType))
                || socketRemoteAddress == null || socketRemoteAddress.isBlank()) {
            throw new IllegalArgumentException("Invalid internal login request");
        }
        if ("password".equals(grantType)) {
            if (!text(username) || !text(rawPassword) || username.length() > 100
                    || email != null || rawEmailCode != null || rawRefreshToken != null) {
                throw new IllegalArgumentException("Invalid internal login request");
            }
        } else if ("email-code".equals(grantType)) {
            new EmailLoginCodeAuthenticationRequest(email, rawEmailCode);
            if (username != null || rawPassword != null || rawRefreshToken != null) {
                throw new IllegalArgumentException("Invalid internal login request");
            }
        } else if (username != null || rawPassword != null || email != null || rawEmailCode != null
                || rawRefreshToken == null || !OPAQUE_TOKEN.matcher(rawRefreshToken).matches()) {
            throw new IllegalArgumentException("Invalid internal login request");
        }
        if (requestedScopes != null) {
            LinkedHashSet<String> scopes = new LinkedHashSet<>();
            for (String scope : requestedScopes) {
                if (!text(scope) || !scopes.add(scope)) {
                    throw new IllegalArgumentException("Invalid internal login request");
                }
            }
            requestedScopes = Set.copyOf(scopes);
        }
    }

    @Override
    public String toString() {
        return "InternalLoginRequest[clientId=" + clientId + ", grantType=" + grantType
                + ", requestedScopes=" + (requestedScopes == null ? "omitted" : requestedScopes.size()) + ']';
    }
    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }
}
