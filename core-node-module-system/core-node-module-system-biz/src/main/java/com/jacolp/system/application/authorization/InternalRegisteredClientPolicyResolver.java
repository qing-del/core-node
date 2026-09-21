package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.InternalRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.OAuth2RegisteredClientMetadata;
import com.jacolp.system.application.authorization.model.PermissionScopePattern;
import com.jacolp.system.application.port.out.OAuth2RegisteredClientMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the fixed USER/ADMIN internal-client policy without handling an HTTP request or token issuance.
 */
@Service
@RequiredArgsConstructor
public class InternalRegisteredClientPolicyResolver {

    private static final Set<String> INTERNAL_CLIENT_IDS = Set.of("user", "admin");
    private static final Set<String> LOGIN_GRANT_TYPES = Set.of("password", "email-code");
    private static final String REFRESH_TOKEN_GRANT = AuthorizationGrantType.REFRESH_TOKEN.getValue();
    private static final Set<String> REQUIRED_GRANT_TYPES = Set.of("password", "email-code", REFRESH_TOKEN_GRANT);
    private static final String INTERNAL_AUTHENTICATION_METHOD = "internal";
    private static final String ACTIVE_STATUS = "active";

    private final RegisteredClientRepository registeredClientRepository;
    private final OAuth2RegisteredClientMetadataRepository metadataRepository;

    public InternalRegisteredClientPolicy resolve(String clientId, String requestedGrantType) {
        if (!INTERNAL_CLIENT_IDS.contains(clientId)) {
            throw new InternalAccountAuthenticationRejectedException(
                    InternalAccountAuthenticationRejectedException.Reason.UNSUPPORTED_CLIENT);
        }
        if (!LOGIN_GRANT_TYPES.contains(requestedGrantType)) {
            throw new InternalAccountAuthenticationRejectedException(
                    InternalAccountAuthenticationRejectedException.Reason.UNSUPPORTED_GRANT_TYPE);
        }
        return resolveInternalPolicy(clientId, requestedGrantType);
    }

    /** Resolves the technical refresh policy without treating refresh_token as a login grant. */
    public InternalRegisteredClientPolicy resolveRefresh(String clientId) {
        if (!INTERNAL_CLIENT_IDS.contains(clientId)) {
            throw new InternalAccountAuthenticationRejectedException(
                    InternalAccountAuthenticationRejectedException.Reason.UNSUPPORTED_CLIENT);
        }
        return resolveInternalPolicy(clientId, REFRESH_TOKEN_GRANT);
    }

    private InternalRegisteredClientPolicy resolveInternalPolicy(String clientId, String requestedGrantType) {
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
        if (registeredClient == null) {
            throw invalid("Registered client is missing or disabled");
        }
        Optional<OAuth2RegisteredClientMetadata> metadataOptional = metadataRepository.findByClientId(clientId);
        if (metadataOptional == null || metadataOptional.isEmpty()) {
            throw invalid("Registered client metadata is missing");
        }
        OAuth2RegisteredClientMetadata metadata = metadataOptional.get();
        verifyIdentity(clientId, registeredClient, metadata);
        verifyNoClientSecret(registeredClient, metadata);
        verifyInternalAuthentication(registeredClient, metadata);
        verifyGrantTypes(registeredClient, metadata, requestedGrantType);
        Duration accessTokenTimeToLive = positiveDuration(registeredClient.getTokenSettings().getAccessTokenTimeToLive(),
                "access token TTL");
        Duration refreshTokenTimeToLive = positiveDuration(registeredClient.getTokenSettings().getRefreshTokenTimeToLive(),
                "refresh token TTL");
        if (registeredClient.getTokenSettings().isReuseRefreshTokens()) {
            throw invalid("Internal client refresh tokens must rotate");
        }

        Set<String> scopes = parseScopeCsv(metadata.scopes(), "client scopes");
        Set<String> autoApproveScopes = parseScopeCsv(metadata.autoApprove(), "auto-approve scopes");
        if (!hasText(metadata.allowedIps())) {
            throw invalid("Client allowed_ips configuration is required");
        }
        return new InternalRegisteredClientPolicy(registeredClient.getId(), clientId, requestedGrantType, scopes,
                autoApproveScopes, metadata.allowedIps(), accessTokenTimeToLive, refreshTokenTimeToLive);
    }

    private static void verifyIdentity(String expectedClientId, RegisteredClient registeredClient,
                                       OAuth2RegisteredClientMetadata metadata) {
        if (!expectedClientId.equals(registeredClient.getClientId())
                || !expectedClientId.equals(metadata.clientId())
                || !hasText(registeredClient.getId())
                || !registeredClient.getId().equals(metadata.id())
                || !ACTIVE_STATUS.equals(metadata.status())) {
            throw invalid("Registered client and metadata identity are inconsistent");
        }
    }

    private static void verifyNoClientSecret(RegisteredClient registeredClient,
                                             OAuth2RegisteredClientMetadata metadata) {
        if (registeredClient.getClientSecret() != null || metadata.clientSecret() != null) {
            throw invalid("Internal clients cannot have a client secret");
        }
    }

    private static void verifyInternalAuthentication(RegisteredClient registeredClient,
                                                     OAuth2RegisteredClientMetadata metadata) {
        if (!registeredClient.getClientAuthenticationMethods()
                .equals(Set.of(new ClientAuthenticationMethod(INTERNAL_AUTHENTICATION_METHOD)))
                || !parseCsv(metadata.clientAuthenticationMethods(), "client authentication methods")
                .equals(Set.of(INTERNAL_AUTHENTICATION_METHOD))) {
            throw invalid("Internal clients must use only the internal authentication method");
        }
    }

    private static void verifyGrantTypes(RegisteredClient registeredClient, OAuth2RegisteredClientMetadata metadata,
                                         String requestedGrantType) {
        Set<String> registeredGrantTypes = registeredClient.getAuthorizationGrantTypes().stream()
                .map(AuthorizationGrantType::getValue)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> metadataGrantTypes = parseCsv(metadata.authorizationGrantTypes(), "client authorization grants");
        if (!registeredGrantTypes.equals(REQUIRED_GRANT_TYPES) || !metadataGrantTypes.equals(REQUIRED_GRANT_TYPES)
                || !registeredGrantTypes.contains(requestedGrantType)) {
            throw invalid("Internal client grants must exactly match the supported login and refresh grants");
        }
    }

    private static Set<String> parseScopeCsv(String value, String source) {
        Set<String> scopes = parseCsv(value, source);
        for (String scope : scopes) {
            try {
                PermissionScopePattern.parse(scope);
            } catch (IllegalArgumentException exception) {
                throw invalid(source + " contains a malformed scope pattern");
            }
        }
        return scopes;
    }

    private static Set<String> parseCsv(String value, String source) {
        if (value == null || value.isBlank()) {
            throw invalid(source + " cannot be blank");
        }
        Set<String> values = new LinkedHashSet<>();
        for (String rawValue : value.split(",", -1)) {
            String normalizedValue = rawValue.trim();
            if (normalizedValue.isEmpty() || !values.add(normalizedValue)) {
                throw invalid(source + " contains an empty or duplicate value");
            }
        }
        return Set.copyOf(values);
    }

    private static Duration positiveDuration(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw invalid(name + " must be positive");
        }
        return duration;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException(message);
    }
}
