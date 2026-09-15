package com.jacolp.system.application.authorization.model;

import com.jacolp.system.application.authorization.ClientAllowedIpPolicy;
import com.jacolp.system.application.authorization.CoreAgentRegisteredClientPolicyResolver;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict application request for one fixed-client CORE AGENT refresh-token grant. */
public record CoreAgentRefreshTokenRequest(
        String clientId,
        String rawRefreshToken,
        List<String> requestedScopes,
        String socketRemoteAddress) {

    private static final Pattern OPAQUE_TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final ClientAllowedIpPolicy ANY_LITERAL_SOCKET_IP =
            ClientAllowedIpPolicy.parse("0.0.0.0/0,::/0");

    public CoreAgentRefreshTokenRequest {
        if (!CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID.equals(clientId)) {
            throw invalid("clientId must be core_agent");
        }
        if (rawRefreshToken == null || !OPAQUE_TOKEN.matcher(rawRefreshToken).matches()) {
            throw invalid("rawRefreshToken must be an opaque Base64URL value");
        }
        requestedScopes = requestedScopes == null ? null : canonicalScopes(requestedScopes);
        if (socketRemoteAddress == null || socketRemoteAddress.isBlank()
                || !socketRemoteAddress.equals(socketRemoteAddress.trim())) {
            throw invalid("socketRemoteAddress must be an IP literal");
        }
        try {
            if (!ANY_LITERAL_SOCKET_IP.allows(socketRemoteAddress)) {
                throw invalid("socketRemoteAddress must be an IP literal");
            }
        } catch (IllegalArgumentException exception) {
            throw invalid("socketRemoteAddress must be an IP literal");
        }
    }

    @Override
    public String toString() {
        return "CoreAgentRefreshTokenRequest[clientId=<redacted>, rawRefreshToken=<redacted>, requestedScopes=<redacted>"
                + ", socketRemoteAddress=<redacted>]";
    }

    private static List<String> canonicalScopes(List<String> scopes) {
        Set<String> canonical = new LinkedHashSet<>();
        for (String scope : scopes) {
            if (scope == null || scope.isBlank()) {
                throw invalid("requestedScopes contains a blank scope");
            }
            String normalized;
            try {
                normalized = PermissionScopePattern.parse(scope).asScope();
            } catch (IllegalArgumentException exception) {
                throw invalid("requestedScopes contains an invalid scope");
            }
            if (!canonical.add(normalized)) {
                throw invalid("requestedScopes contains a duplicate scope");
            }
        }
        List<String> sorted = new ArrayList<>(canonical);
        sorted.sort(String::compareTo);
        return List.copyOf(sorted);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid CORE AGENT refresh-token request: " + message);
    }
}
