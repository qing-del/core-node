package com.jacolp.system.web.authorization;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationCodeRequestAuthenticationConverter;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationConsentAuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

/**
 * Adds only verified browser request details to Spring Authorization Server's official converters.
 * OAuth parameter parsing and its protocol errors remain entirely owned by the SAS 7.0.4 delegates.
 */
@Component
public final class CoreAgentAuthorizationEndpointAuthenticationConverter implements AuthenticationConverter {

    static final String CONSENT_ACTION_PARAMETER = "consent_action";
    static final String INVALID_CONSENT_ACTION_DESCRIPTION = "Invalid CORE AGENT consent action";

    private final RequestMatcher authorizationEndpointMatcher =
            PathPatternRequestMatcher.pathPattern("/oauth2/authorize");
    private final AuthenticationConverter authorizationRequestConverter =
            new OAuth2AuthorizationCodeRequestAuthenticationConverter();
    private final AuthenticationConverter authorizationConsentConverter =
            new OAuth2AuthorizationConsentAuthenticationConverter();

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!authorizationEndpointMatcher.matches(request)) {
            return null;
        }
        Authentication authentication = authorizationRequestConverter.convert(request);
        if (authentication instanceof OAuth2AuthorizationCodeRequestAuthenticationToken token) {
            return token;
        }
        if (authentication != null) {
            return null;
        }

        authentication = authorizationConsentConverter.convert(request);
        if (authentication instanceof OAuth2AuthorizationConsentAuthenticationToken token) {
            return token;
        }
        return null;
    }

}
