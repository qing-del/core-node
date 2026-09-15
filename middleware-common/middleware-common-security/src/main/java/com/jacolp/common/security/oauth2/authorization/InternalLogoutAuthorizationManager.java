package com.jacolp.common.security.oauth2.authorization;

import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.common.security.context.SecurityContextCurrentPrincipalAccessor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Objects;
import java.util.function.Supplier;

/** Allows the non-catalogue internal logout endpoint only to a validated USER or ADMIN client JWT. */
public final class InternalLogoutAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final AuthorizationDecision DENY = new AuthorizationDecision(false);
    private static final AuthorizationDecision ALLOW = new AuthorizationDecision(true);

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
            CurrentPrincipal principal = SecurityContextCurrentPrincipalAccessor.fromJwt(jwtAuthentication.getToken());
            return "user".equals(principal.clientId()) || "admin".equals(principal.clientId()) ? ALLOW : DENY;
        } catch (RuntimeException ignored) {
            return DENY;
        }
    }
}
