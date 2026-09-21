package com.jacolp.system.web.authorization;

import com.jacolp.system.application.authorization.ClientAllowedIpPolicy;

/**
 * Minimal transport detail attached to a CORE AGENT authorization-code token request.
 *
 * <p>Only the direct socket peer is retained. OAuth parameters, client credentials, verifier, code, headers,
 * and servlet session state deliberately remain absent.</p>
 */
public record CoreAgentAuthorizationCodeTokenRequestDetails(String socketRemoteAddress) {

    private static final ClientAllowedIpPolicy ANY_LITERAL_SOCKET_IP =
            ClientAllowedIpPolicy.parse("0.0.0.0/0,::/0");

    public CoreAgentAuthorizationCodeTokenRequestDetails {
        if (socketRemoteAddress == null || socketRemoteAddress.isBlank()
                || !socketRemoteAddress.equals(socketRemoteAddress.trim())) {
            throw new IllegalArgumentException("CORE AGENT token socket address is invalid");
        }
        try {
            if (!ANY_LITERAL_SOCKET_IP.allows(socketRemoteAddress)) {
                throw new IllegalArgumentException("CORE AGENT token socket address is invalid");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("CORE AGENT token socket address is invalid");
        }
    }

    @Override
    public String toString() {
        return "CoreAgentAuthorizationCodeTokenRequestDetails[socketRemoteAddress=<redacted>]";
    }
}
