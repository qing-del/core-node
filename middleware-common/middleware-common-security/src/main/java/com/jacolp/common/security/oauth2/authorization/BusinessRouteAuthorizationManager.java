package com.jacolp.common.security.oauth2.authorization;

import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.common.security.context.SecurityContextCurrentPrincipalAccessor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Fail-closed adapter from a validated CORE NODE RS256 authentication to the business route catalogue.
 * It intentionally has no filter-chain registration: OAuth protocol routes must not use this adapter.
 */
public final class BusinessRouteAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final AuthorizationDecision DENY = new AuthorizationDecision(false);
    private static final AuthorizationDecision ALLOW = new AuthorizationDecision(true);

    private final BusinessRouteAuthorizationPolicy policy;

    public BusinessRouteAuthorizationManager(BusinessRouteAuthorizationPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                         RequestAuthorizationContext context) {
        Objects.requireNonNull(authentication, "authentication must not be null");
        Objects.requireNonNull(context, "context must not be null");
        try {
            Authentication current = authentication.get();
            if (!(current instanceof JwtAuthenticationToken jwtAuthentication) || !current.isAuthenticated()) {
                return DENY;
            }
            HttpMethod method = HttpMethod.valueOf(context.getRequest().getMethod());
            CurrentPrincipal principal = SecurityContextCurrentPrincipalAccessor.fromJwt(jwtAuthentication.getToken());
            return policy.authorize(method, applicationPath(context.getRequest()), principal)
                    == BusinessRouteAuthorizationPolicy.Decision.ALLOW ? ALLOW : DENY;
        } catch (RuntimeException ignored) {
            return DENY;
        }
    }

    private static String applicationPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length()) : uri;
    }
}
