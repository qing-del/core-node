package com.jacolp.system.web.authorization;

import com.jacolp.system.application.authorization.ClientAllowedIpPolicy;

/**
 * Minimal transport detail for a CORE AGENT refresh-token request.
 * Raw refresh credentials, headers, servlet session state, and parsed scopes are intentionally absent.
 */
public record CoreAgentRefreshTokenRequestDetails(String socketRemoteAddress, boolean originalScopeParameterPresent) {

    private static final ClientAllowedIpPolicy ANY_LITERAL_SOCKET_IP =
            ClientAllowedIpPolicy.parse("0.0.0.0/0,::/0");

    public CoreAgentRefreshTokenRequestDetails {
        if (socketRemoteAddress == null || socketRemoteAddress.isBlank()
                || !socketRemoteAddress.equals(socketRemoteAddress.trim())) {
            throw new IllegalArgumentException("CORE AGENT refresh socket address is invalid");
        }
        try {
            if (!ANY_LITERAL_SOCKET_IP.allows(socketRemoteAddress)) {
                throw new IllegalArgumentException("CORE AGENT refresh socket address is invalid");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("CORE AGENT refresh socket address is invalid");
        }
    }

    @Override
    public String toString() {
        return "CoreAgentRefreshTokenRequestDetails[socketRemoteAddress=<redacted>, originalScopeParameterPresent="
                + originalScopeParameterPresent + ']';
    }
}
