package com.jacolp.system.web.authorization;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationCodeAuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

/**
 * Leaves authorization-code token parameter parsing and protocol errors to the SAS 7.0.4 converter, while
 * attaching only the direct socket peer needed by the project token provider.
 */
@Component
public final class CoreAgentAuthorizationCodeTokenAuthenticationConverter implements AuthenticationConverter {

    private final RequestMatcher tokenEndpointMatcher =
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/oauth/token");
    private final AuthenticationConverter delegate = new OAuth2AuthorizationCodeAuthenticationConverter();

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!tokenEndpointMatcher.matches(request)) {
            return null;
        }
        Authentication authentication = delegate.convert(request);
        if (authentication instanceof OAuth2AuthorizationCodeAuthenticationToken token) {
            return token;
        }
        return null;
    }
}
