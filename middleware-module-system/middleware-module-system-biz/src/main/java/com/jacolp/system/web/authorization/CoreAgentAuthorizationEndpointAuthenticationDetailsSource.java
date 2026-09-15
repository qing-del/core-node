package com.jacolp.system.web.authorization;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

/** Supplies server-owned browser details after SAS has parsed or prevalidated an authorization request. */
public final class CoreAgentAuthorizationEndpointAuthenticationDetailsSource
        implements AuthenticationDetailsSource<HttpServletRequest, CoreAgentAuthorizationEndpointRequestDetails> {

    @Override
    public CoreAgentAuthorizationEndpointRequestDetails buildDetails(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        return new CoreAgentAuthorizationEndpointRequestDetails(session, session.getId(), request.getRemoteAddr(),
                request.getParameterMap().containsKey("scope"), consentAction(request));
    }

    private static CoreAgentAuthorizationEndpointRequestDetails.ConsentAction consentAction(HttpServletRequest request) {
        String[] values = request.getParameterValues(CoreAgentAuthorizationEndpointAuthenticationConverter.CONSENT_ACTION_PARAMETER);
        if (values == null) {
            if ("POST".equals(request.getMethod())) {
                throw invalidConsentAction();
            }
            return null;
        }
        if (values.length != 1) {
            throw invalidConsentAction();
        }
        return switch (values[0]) {
            case "approve" -> CoreAgentAuthorizationEndpointRequestDetails.ConsentAction.APPROVE;
            case "deny" -> CoreAgentAuthorizationEndpointRequestDetails.ConsentAction.DENY;
            default -> throw invalidConsentAction();
        };
    }

    private static OAuth2AuthenticationException invalidConsentAction() {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST,
                CoreAgentAuthorizationEndpointAuthenticationConverter.INVALID_CONSENT_ACTION_DESCRIPTION, null));
    }
}
