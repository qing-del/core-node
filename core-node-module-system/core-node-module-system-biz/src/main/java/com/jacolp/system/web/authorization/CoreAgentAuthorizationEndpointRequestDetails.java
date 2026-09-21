package com.jacolp.system.web.authorization;

import com.jacolp.system.application.authorization.ClientAllowedIpPolicy;
import jakarta.servlet.http.HttpSession;

/**
 * Request-bound browser data made available to the custom CORE AGENT authorization providers.
 *
 * <p>This web-only object is deliberately not serializable and never writes transaction state to
 * the session. The provider decides when a validated transaction is stored.</p>
 */
public record CoreAgentAuthorizationEndpointRequestDetails(
        HttpSession session,
        String sessionId,
        String socketRemoteAddress,
        boolean originalScopeParameterPresent,
        ConsentAction consentAction) {

    private static final ClientAllowedIpPolicy ANY_LITERAL_SOCKET_IP =
            ClientAllowedIpPolicy.parse("0.0.0.0/0,::/0");

    public CoreAgentAuthorizationEndpointRequestDetails {
        if (session == null || sessionId == null || sessionId.isBlank() || !sessionId.equals(session.getId())) {
            throw new IllegalArgumentException("CORE AGENT authorization session is invalid");
        }
        if (socketRemoteAddress == null || socketRemoteAddress.isBlank()
                || !socketRemoteAddress.equals(socketRemoteAddress.trim())) {
            throw new IllegalArgumentException("CORE AGENT authorization socket address is invalid");
        }
        try {
            if (!ANY_LITERAL_SOCKET_IP.allows(socketRemoteAddress)) {
                throw new IllegalArgumentException("CORE AGENT authorization socket address is invalid");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("CORE AGENT authorization socket address is invalid");
        }
    }

    @Override
    public String toString() {
        return "CoreAgentAuthorizationEndpointRequestDetails[session=<redacted>, sessionId=<redacted>"
                + ", socketRemoteAddress=<redacted>, originalScopeParameterPresent=" + originalScopeParameterPresent
                + ", consentAction=<redacted>]";
    }

    /** Explicit browser decision sent only by the custom consent form. */
    public enum ConsentAction {
        APPROVE,
        DENY
    }
}
