package com.jacolp.system.web.authorization;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2RefreshTokenAuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

/**
 * Delegates refresh-token parameter parsing and protocol errors to SAS 7.0.4 while adding only direct socket
 * provenance and whether the caller supplied a scope parameter.
 */
@Component
public final class CoreAgentRefreshTokenAuthenticationConverter implements AuthenticationConverter {

    private final RequestMatcher tokenEndpointMatcher =
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/oauth/token");
    private final AuthenticationConverter delegate = new OAuth2RefreshTokenAuthenticationConverter();

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!tokenEndpointMatcher.matches(request)) {
            return null;
        }
        Authentication authentication = delegate.convert(request);
        if (authentication instanceof OAuth2RefreshTokenAuthenticationToken token) {
            return token;
        }
        return null;
    }
}
