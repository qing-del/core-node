package com.jacolp.system.web.authorization;

import com.jacolp.system.application.authorization.CoreAgentRefreshTokenRejectedException;
import com.jacolp.system.application.authorization.CoreAgentRefreshTokenService;
import com.jacolp.system.application.authorization.CoreAgentRegisteredClientPolicyResolver;
import com.jacolp.system.application.authorization.model.CoreAgentRefreshTokenRequest;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentRefreshTokens;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Maps only SAS's official refresh token request into the project-owned CORE AGENT refresh service. */
@Component
public final class CoreAgentRefreshTokenAuthenticationProvider implements AuthenticationProvider {

    private static final String INVALID_GRANT_DESCRIPTION = "Invalid CORE AGENT refresh token grant";
    private static final String INVALID_CLIENT_DESCRIPTION = "Invalid CORE AGENT client authentication";
    private static final String UNAUTHORIZED_CLIENT_DESCRIPTION = "CORE AGENT client is not authorized";
    private static final Set<AuthorizationGrantType> EXPECTED_GRANT_TYPES = Set.of(
            AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN);

    private final CoreAgentRegisteredClientPolicyResolver policyResolver;
    private final CoreAgentRefreshTokenService refreshTokenService;

    public CoreAgentRefreshTokenAuthenticationProvider(CoreAgentRegisteredClientPolicyResolver policyResolver,
                                                       CoreAgentRefreshTokenService refreshTokenService) {
        this.policyResolver = Objects.requireNonNull(policyResolver, "policyResolver");
        this.refreshTokenService = Objects.requireNonNull(refreshTokenService, "refreshTokenService");
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        if (!(authentication instanceof OAuth2RefreshTokenAuthenticationToken request) || request.isAuthenticated()) {
            throw invalidGrant();
        }
        OAuth2ClientAuthenticationToken clientPrincipal = authenticatedClient(request);
        CoreAgentRegisteredClientPolicy policy = requiredPolicy();
        RegisteredClient registeredClient = requiredRegisteredClient(clientPrincipal, policy);
        CoreAgentRefreshTokenRequestDetails details = requestDetails(request);
        List<String> requestedScopes = requestedScopes(request, details);

        final CoreAgentRefreshTokenRequest refreshRequest;
        try {
            refreshRequest = new CoreAgentRefreshTokenRequest(policy.clientId(), request.getRefreshToken(),
                    requestedScopes, details.socketRemoteAddress());
        } catch (IllegalArgumentException exception) {
            throw invalidGrant();
        }
        final IssuedCoreAgentRefreshTokens issuedTokens;
        try {
            issuedTokens = refreshTokenService.refresh(refreshRequest);
            if (issuedTokens == null) {
                throw new IllegalStateException("CORE AGENT refresh service returned null");
            }
        } catch (CoreAgentRefreshTokenRejectedException exception) {
            throw invalidGrant();
        }
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                issuedTokens.accessToken(), issuedTokens.accessIssuedAt(), issuedTokens.accessExpiresAt(),
                Set.copyOf(issuedTokens.grantedScopes()));
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(issuedTokens.refreshToken(),
                issuedTokens.refreshIssuedAt(), issuedTokens.refreshExpiresAt());
        return new OAuth2AccessTokenAuthenticationToken(registeredClient, clientPrincipal, accessToken, refreshToken,
                Map.of());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2RefreshTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static OAuth2ClientAuthenticationToken authenticatedClient(OAuth2RefreshTokenAuthenticationToken request) {
        if (!(request.getPrincipal() instanceof OAuth2ClientAuthenticationToken clientPrincipal)
                || !clientPrincipal.isAuthenticated() || clientPrincipal.getRegisteredClient() == null
                || clientPrincipal.getCredentials() != null
                || !ClientAuthenticationMethod.NONE.equals(clientPrincipal.getClientAuthenticationMethod())) {
            throw invalidClient();
        }
        return clientPrincipal;
    }

    private CoreAgentRegisteredClientPolicy requiredPolicy() {
        CoreAgentRegisteredClientPolicy policy = policyResolver.resolve(
                CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID);
        if (policy == null || !CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID.equals(policy.clientId())
                || policy.registeredClientId() == null || policy.registeredClientId().isBlank()) {
            throw unauthorizedClient();
        }
        return policy;
    }

    private static RegisteredClient requiredRegisteredClient(OAuth2ClientAuthenticationToken clientPrincipal,
                                                             CoreAgentRegisteredClientPolicy policy) {
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
        if (!policy.clientId().equals(clientPrincipal.getPrincipal())
                || !policy.clientId().equals(registeredClient.getClientId())
                || !policy.registeredClientId().equals(registeredClient.getId())) {
            throw unauthorizedClient();
        }
        if (!Set.of(ClientAuthenticationMethod.NONE).equals(registeredClient.getClientAuthenticationMethods())
                || registeredClient.getClientSecret() != null || registeredClient.getClientSecretExpiresAt() != null
                || !EXPECTED_GRANT_TYPES.equals(registeredClient.getAuthorizationGrantTypes())
                || !Set.of(policy.redirectUri()).equals(registeredClient.getRedirectUris())
                || !policy.scopes().equals(registeredClient.getScopes())
                || registeredClient.getClientSettings() == null
                || !registeredClient.getClientSettings().isRequireProofKey()
                || !registeredClient.getClientSettings().isRequireAuthorizationConsent()) {
            throw unauthorizedClient();
        }
        return registeredClient;
    }

    private static CoreAgentRefreshTokenRequestDetails requestDetails(OAuth2RefreshTokenAuthenticationToken request) {
        if (!(request.getDetails() instanceof CoreAgentRefreshTokenRequestDetails details)) {
            throw invalidGrant();
        }
        return details;
    }

    private static List<String> requestedScopes(OAuth2RefreshTokenAuthenticationToken request,
                                                CoreAgentRefreshTokenRequestDetails details) {
        if (!details.originalScopeParameterPresent()) {
            return null;
        }
        return request.getScopes() == null ? List.of() : List.copyOf(request.getScopes());
    }

    private static OAuth2AuthenticationException invalidGrant() {
        return error(OAuth2ErrorCodes.INVALID_GRANT, INVALID_GRANT_DESCRIPTION);
    }

    private static OAuth2AuthenticationException invalidClient() {
        return error(OAuth2ErrorCodes.INVALID_CLIENT, INVALID_CLIENT_DESCRIPTION);
    }

    private static OAuth2AuthenticationException unauthorizedClient() {
        return error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, UNAUTHORIZED_CLIENT_DESCRIPTION);
    }

    private static OAuth2AuthenticationException error(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null));
    }
}
