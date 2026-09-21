package com.jacolp.common.security.oauth2.authorization;

import com.jacolp.common.security.context.CurrentPrincipal;
import org.springframework.http.HttpMethod;

/** Authorizes one already-authenticated request against the immutable business-route catalogue. */
public interface BusinessRouteAuthorizationPolicy {

    Decision authorize(HttpMethod method, String requestPath, CurrentPrincipal principal);

    enum Decision {
        ALLOW,
        NO_MATCH,
        CLIENT_MISMATCH,
        ROLE_MISMATCH,
        SCOPE_MISMATCH
    }
}
