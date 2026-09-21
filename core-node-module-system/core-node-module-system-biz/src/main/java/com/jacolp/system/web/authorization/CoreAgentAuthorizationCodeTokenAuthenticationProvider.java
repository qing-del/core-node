package com.jacolp.system.web.authorization;

import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationCodeExchangeRequest;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationCodeTokens;
import com.jacolp.system.application.authorization.model.VerifiedCoreAgentAuthorizationCode;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationCodeExchangeRejectedException;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationCodeExchangeService;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationCodeTokenRejectedException;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationCodeTokenService;
import com.jacolp.system.application.authorization.CoreAgentRegisteredClientPolicyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Exchanges only the project-owned Redis-backed CORE AGENT authorization code for the project token pair.
 * It deliberately does not use Spring Authorization Server's authorization persistence or token generator.
 */
@Component
public final class CoreAgentAuthorizationCodeTokenAuthenticationProvider implements AuthenticationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoreAgentAuthorizationCodeTokenAuthenticationProvider.class);
    private static final String INVALID_GRANT_DESCRIPTION = "Invalid CORE AGENT authorization code grant";
    private static final String INVALID_CLIENT_DESCRIPTION = "Invalid CORE AGENT client authentication";
    private static final String UNAUTHORIZED_CLIENT_DESCRIPTION = "CORE AGENT client is not authorized";
    private static final Set<AuthorizationGrantType> EXPECTED_GRANT_TYPES = Set.of(
            AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN);

    private final CoreAgentRegisteredClientPolicyResolver policyResolver;
    private final CoreAgentAuthorizationCodeExchangeService exchangeService;
    private final CoreAgentAuthorizationCodeTokenService tokenService;

    public CoreAgentAuthorizationCodeTokenAuthenticationProvider(
            CoreAgentRegisteredClientPolicyResolver policyResolver,
            CoreAgentAuthorizationCodeExchangeService exchangeService,
            CoreAgentAuthorizationCodeTokenService tokenService) {
        this.policyResolver = Objects.requireNonNull(policyResolver, "policyResolver");
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService");
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthorizationCodeAuthenticationToken request)
                || request.isAuthenticated()) {
            throw invalidGrant();
        }
        OAuth2ClientAuthenticationToken clientPrincipal = authenticatedClient(request);
        CoreAgentRegisteredClientPolicy policy = requiredPolicy();
        RegisteredClient registeredClient = requiredRegisteredClient(clientPrincipal, policy);
        CoreAgentAuthorizationCodeTokenRequestDetails details = requestDetails(request);
        String codeVerifier = requiredCodeVerifier(request);

        CoreAgentAuthorizationCodeExchangeRequest exchangeRequest;
        try {
            exchangeRequest = new CoreAgentAuthorizationCodeExchangeRequest(request.getCode(), policy.clientId(),
                    request.getRedirectUri(), codeVerifier, details.socketRemoteAddress());
        } catch (IllegalArgumentException exception) {
            throw invalidGrant();
        }

        final VerifiedCoreAgentAuthorizationCode verifiedCode;
        try {
            verifiedCode = exchangeService.exchange(exchangeRequest);
            if (verifiedCode == null) {
                throw new IllegalStateException("CORE AGENT authorization-code exchange returned null");
            }
        } catch (CoreAgentAuthorizationCodeExchangeRejectedException exception) {
            throw invalidGrant();
        }

        final IssuedCoreAgentAuthorizationCodeTokens issuedTokens;
        try {
            issuedTokens = tokenService.issue(verifiedCode);
            if (issuedTokens == null) {
                throw new IllegalStateException("CORE AGENT authorization-code token service returned null");
            }
        } catch (CoreAgentAuthorizationCodeTokenRejectedException exception) {
            throw invalidGrant();
        }

        if (issuedTokens.socketAddressChanged()) {
            LOGGER.warn("CORE AGENT authorization-code token issued with changed socket address for client core_agent");
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
        return OAuth2AuthorizationCodeAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static OAuth2ClientAuthenticationToken authenticatedClient(
            OAuth2AuthorizationCodeAuthenticationToken request) {
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

    private static CoreAgentAuthorizationCodeTokenRequestDetails requestDetails(
            OAuth2AuthorizationCodeAuthenticationToken request) {
        if (!(request.getDetails() instanceof CoreAgentAuthorizationCodeTokenRequestDetails details)) {
            throw invalidGrant();
        }
        return details;
    }

    private static String requiredCodeVerifier(OAuth2AuthorizationCodeAuthenticationToken request) {
        Object value = request.getAdditionalParameters().get("code_verifier");
        if (!(value instanceof String verifier) || verifier.isBlank() || !verifier.equals(verifier.trim())) {
            throw invalidGrant();
        }
        return verifier;
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
