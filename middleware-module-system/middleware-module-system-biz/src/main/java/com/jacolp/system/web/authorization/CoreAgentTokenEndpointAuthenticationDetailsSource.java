package com.jacolp.system.web.authorization;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationDetailsSource;

/** Supplies direct socket provenance for the two supported CORE AGENT token grants. */
public final class CoreAgentTokenEndpointAuthenticationDetailsSource
        implements AuthenticationDetailsSource<HttpServletRequest, Object> {

    @Override
    public Object buildDetails(HttpServletRequest request) {
        String grantType = request.getParameter("grant_type");
        if ("authorization_code".equals(grantType)) {
            return new CoreAgentAuthorizationCodeTokenRequestDetails(request.getRemoteAddr());
        }
        if ("refresh_token".equals(grantType)) {
            return new CoreAgentRefreshTokenRequestDetails(request.getRemoteAddr(),
                    request.getParameterMap().containsKey("scope"));
        }
        return null;
    }
}
