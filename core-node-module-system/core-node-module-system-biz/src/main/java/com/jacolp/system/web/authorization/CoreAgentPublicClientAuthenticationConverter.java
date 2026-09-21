package com.jacolp.system.web.authorization;

import com.jacolp.system.application.authorization.CoreAgentRegisteredClientPolicyResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Authenticates only the fixed public CORE AGENT identity at the token endpoint.
 *
 * <p>SAS's stock public-client converter deliberately requires {@code code_verifier}, so it cannot
 * authenticate a refresh-token request. This converter performs no PKCE work: the project-owned
 * authorization-code exchange service is the sole verifier for that proof.</p>
 */
@Component
public final class CoreAgentPublicClientAuthenticationConverter implements AuthenticationConverter {

    private static final Set<String> SUPPORTED_GRANTS = Set.of(
            AuthorizationGrantType.AUTHORIZATION_CODE.getValue(), AuthorizationGrantType.REFRESH_TOKEN.getValue());

    private final RequestMatcher tokenEndpointMatcher =
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/oauth/token");

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!tokenEndpointMatcher.matches(request)) {
            return null;
        }
        String grantType = supportedGrantType(request);
        if (grantType == null) {
            return null;
        }
        String clientId = requiredClientId(request);
        rejectCredentials(request);
        OAuth2ClientAuthenticationToken authentication = new OAuth2ClientAuthenticationToken(clientId,
                ClientAuthenticationMethod.NONE, null, Map.of("core_agent_grant_type", grantType));
        return authentication;
    }

    private static String supportedGrantType(HttpServletRequest request) {
        String[] values = request.getParameterValues("grant_type");
        if (values == null || values.length != 1 || values[0] == null || !SUPPORTED_GRANTS.contains(values[0])) {
            return null;
        }
        return values[0];
    }

    private static String requiredClientId(HttpServletRequest request) {
        String[] values = request.getParameterValues("client_id");
        if (values == null || values.length != 1 || values[0] == null
                || !CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID.equals(values[0])) {
            throw invalidClient();
        }
        return values[0];
    }

    private static void rejectCredentials(HttpServletRequest request) {
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null
                || request.getParameterMap().containsKey("client_secret")
                || request.getParameterMap().containsKey("client_assertion")
                || request.getParameterMap().containsKey("client_assertion_type")) {
            throw invalidClient();
        }
    }

    private static OAuth2AuthenticationException invalidClient() {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT,
                "Invalid CORE AGENT client authentication", null));
    }
}
