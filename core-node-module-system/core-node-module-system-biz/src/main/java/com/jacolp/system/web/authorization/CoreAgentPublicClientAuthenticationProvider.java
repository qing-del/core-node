package com.jacolp.system.web.authorization;

import com.jacolp.system.application.authorization.CoreAgentRegisteredClientPolicyResolver;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.infrastructure.authorization.ActiveRegisteredClientRepository;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/**
 * Authenticates the one fixed public client without using SAS authorization persistence.
 *
 * <p>PKCE is intentionally not checked here. The Redis-backed authorization-code exchange is the
 * only PKCE verifier, which avoids an incompatible SAS authorization-record dependency.</p>
 */
@Component
public final class CoreAgentPublicClientAuthenticationProvider implements AuthenticationProvider {

    private static final Set<AuthorizationGrantType> EXPECTED_GRANT_TYPES = Set.of(
            AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN);

    private final CoreAgentRegisteredClientPolicyResolver policyResolver;
    private final ActiveRegisteredClientRepository registeredClientRepository;

    public CoreAgentPublicClientAuthenticationProvider(CoreAgentRegisteredClientPolicyResolver policyResolver,
                                                       ActiveRegisteredClientRepository registeredClientRepository) {
        this.policyResolver = Objects.requireNonNull(policyResolver, "policyResolver");
        this.registeredClientRepository = Objects.requireNonNull(registeredClientRepository,
                "registeredClientRepository");
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        if (!(authentication instanceof OAuth2ClientAuthenticationToken request) || request.isAuthenticated()) {
            throw invalidClient();
        }
        if (!ClientAuthenticationMethod.NONE.equals(request.getClientAuthenticationMethod())
                || request.getCredentials() != null
                || !CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID.equals(request.getPrincipal())) {
            throw invalidClient();
        }
        Object grantType = request.getAdditionalParameters().get("core_agent_grant_type");
        if (!(grantType instanceof String requestedGrantType)
                || !Set.of(AuthorizationGrantType.AUTHORIZATION_CODE.getValue(), AuthorizationGrantType.REFRESH_TOKEN.getValue())
                .contains(requestedGrantType)) {
            throw invalidClient();
        }
        CoreAgentRegisteredClientPolicy policy = requiredPolicy();
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(policy.clientId());
        if (!matchesPolicy(registeredClient, policy, requestedGrantType)) {
            throw unauthorizedClient();
        }
        return new OAuth2ClientAuthenticationToken(registeredClient, ClientAuthenticationMethod.NONE, null);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private CoreAgentRegisteredClientPolicy requiredPolicy() {
        CoreAgentRegisteredClientPolicy policy = policyResolver.resolve(
                CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID);
        if (policy == null || !CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID.equals(policy.clientId())
                || policy.registeredClientId() == null || policy.registeredClientId().isBlank()) {
            throw invalidClient();
        }
        return policy;
    }

    private static boolean matchesPolicy(RegisteredClient client, CoreAgentRegisteredClientPolicy policy,
                                         String requestedGrantType) {
        return client != null
                && policy.registeredClientId().equals(client.getId())
                && policy.clientId().equals(client.getClientId())
                && Set.of(ClientAuthenticationMethod.NONE).equals(client.getClientAuthenticationMethods())
                && client.getClientSecret() == null
                && client.getClientSecretExpiresAt() == null
                && EXPECTED_GRANT_TYPES.equals(client.getAuthorizationGrantTypes())
                && client.getAuthorizationGrantTypes().stream().map(AuthorizationGrantType::getValue)
                .anyMatch(requestedGrantType::equals)
                && Set.of(policy.redirectUri()).equals(client.getRedirectUris())
                && policy.scopes().equals(client.getScopes())
                && client.getClientSettings() != null
                && client.getClientSettings().isRequireProofKey()
                && client.getClientSettings().isRequireAuthorizationConsent();
    }

    private static OAuth2AuthenticationException invalidClient() {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT,
                "Invalid CORE AGENT client authentication", null));
    }

    private static OAuth2AuthenticationException unauthorizedClient() {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT,
                "CORE AGENT client is not authorized", null));
    }
}
