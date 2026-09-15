package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.OAuth2RegisteredClientMetadata;
import com.jacolp.system.application.authorization.model.PermissionScopePattern;
import com.jacolp.system.application.port.out.OAuth2RegisteredClientMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the sole Phase 4 public-client catalogue entry into an application-neutral policy.
 *
 * <p>The final fixed catalogue deliberately requires exact grant and redirect URI sets. Although
 * Spring Authorization Server permits additional values, accepting them here would silently widen
 * the public CORE AGENT attack surface. Every malformed, missing, or mismatched source fails closed.</p>
 */
@Service
@RequiredArgsConstructor
public class CoreAgentRegisteredClientPolicyResolver {

    public static final String CORE_AGENT_CLIENT_ID = "core_agent";
    public static final String CORE_AGENT_REGISTERED_CLIENT_ID = "e7cf5b30-8e43-4db2-bc53-000000000003";
    public static final String CORE_AGENT_REDIRECT_URI = "http://127.0.0.1:9090/oauth/callback";

    private static final String ACTIVE_STATUS = "active";
    private static final String PUBLIC_AUTHENTICATION_METHOD = "none";
    private static final String ALLOWED_IPS = "0.0.0.0/0";
    private static final Set<String> EXPECTED_GRANT_TYPES = Set.of(
            AuthorizationGrantType.AUTHORIZATION_CODE.getValue(), AuthorizationGrantType.REFRESH_TOKEN.getValue());
    private static final Set<String> EXPECTED_REDIRECT_URIS = Set.of(CORE_AGENT_REDIRECT_URI);
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(1);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofHours(24);
    private static final Duration AUTHORIZATION_CODE_TTL = Duration.ofMinutes(10);

    private final RegisteredClientRepository registeredClientRepository;
    private final OAuth2RegisteredClientMetadataRepository metadataRepository;

    public CoreAgentRegisteredClientPolicy resolve(String clientId) {
        if (!CORE_AGENT_CLIENT_ID.equals(clientId)) {
            throw invalid("Only the core_agent public client is supported");
        }
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
        if (registeredClient == null) {
            throw invalid("CORE AGENT registered client is missing or disabled");
        }
        Optional<OAuth2RegisteredClientMetadata> metadataOptional = metadataRepository.findByClientId(clientId);
        if (metadataOptional == null || metadataOptional.isEmpty()) {
            throw invalid("CORE AGENT client metadata is missing");
        }
        OAuth2RegisteredClientMetadata metadata = metadataOptional.get();

        verifyIdentity(registeredClient, metadata);
        verifyNoClientSecret(registeredClient, metadata);
        verifyPublicAuthentication(registeredClient, metadata);
        verifyGrantTypes(registeredClient, metadata);
        String redirectUri = verifyRedirectUris(registeredClient, metadata);
        Set<String> scopes = verifyScopes(registeredClient, metadata);
        Set<String> autoApproveScopes = parseScopeCsv(metadata.autoApprove(), "CORE AGENT auto-approve scopes");
        verifyAutoApproveIsClientBound(scopes, autoApproveScopes);
        verifyAllowedIps(metadata);
        verifyClientSettings(registeredClient.getClientSettings());
        verifyTokenSettings(registeredClient.getTokenSettings());

        return new CoreAgentRegisteredClientPolicy(registeredClient.getId(), CORE_AGENT_CLIENT_ID, redirectUri,
                scopes, autoApproveScopes, metadata.allowedIps(), ACCESS_TOKEN_TTL, REFRESH_TOKEN_TTL,
                AUTHORIZATION_CODE_TTL);
    }

    private static void verifyIdentity(RegisteredClient registeredClient, OAuth2RegisteredClientMetadata metadata) {
        if (!CORE_AGENT_REGISTERED_CLIENT_ID.equals(registeredClient.getId())
                || !CORE_AGENT_CLIENT_ID.equals(registeredClient.getClientId())
                || !CORE_AGENT_REGISTERED_CLIENT_ID.equals(metadata.id())
                || !CORE_AGENT_CLIENT_ID.equals(metadata.clientId())
                || !ACTIVE_STATUS.equals(metadata.status())) {
            throw invalid("CORE AGENT registered client and metadata identity are inconsistent");
        }
    }

    private static void verifyNoClientSecret(RegisteredClient registeredClient,
                                             OAuth2RegisteredClientMetadata metadata) {
        if (registeredClient.getClientSecret() != null
                || registeredClient.getClientSecretExpiresAt() != null
                || metadata.clientSecret() != null
                || metadata.clientSecretExpiresAt() != null) {
            throw invalid("CORE AGENT must remain a public client without a client secret");
        }
    }

    private static void verifyPublicAuthentication(RegisteredClient registeredClient,
                                                   OAuth2RegisteredClientMetadata metadata) {
        if (!Set.of(ClientAuthenticationMethod.NONE).equals(registeredClient.getClientAuthenticationMethods())
                || !Set.of(PUBLIC_AUTHENTICATION_METHOD).equals(
                parseCsv(metadata.clientAuthenticationMethods(), "CORE AGENT client authentication methods"))) {
            throw invalid("CORE AGENT must use only the none client authentication method");
        }
    }

    private static void verifyGrantTypes(RegisteredClient registeredClient, OAuth2RegisteredClientMetadata metadata) {
        Set<String> registeredGrantTypes = registeredClient.getAuthorizationGrantTypes().stream()
                .map(AuthorizationGrantType::getValue)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> metadataGrantTypes = parseCsv(metadata.authorizationGrantTypes(), "CORE AGENT authorization grants");
        if (!EXPECTED_GRANT_TYPES.equals(registeredGrantTypes) || !EXPECTED_GRANT_TYPES.equals(metadataGrantTypes)) {
            throw invalid("CORE AGENT must allow exactly authorization_code and refresh_token");
        }
    }

    private static String verifyRedirectUris(RegisteredClient registeredClient, OAuth2RegisteredClientMetadata metadata) {
        Set<String> registeredRedirectUris = parseUriSet(registeredClient.getRedirectUris(), "CORE AGENT registered redirect URIs");
        Set<String> metadataRedirectUris = parseCsv(metadata.redirectUris(), "CORE AGENT metadata redirect URIs");
        if (!EXPECTED_REDIRECT_URIS.equals(registeredRedirectUris) || !EXPECTED_REDIRECT_URIS.equals(metadataRedirectUris)) {
            throw invalid("CORE AGENT must use exactly its registered redirect URI");
        }
        return CORE_AGENT_REDIRECT_URI;
    }

    private static Set<String> verifyScopes(RegisteredClient registeredClient, OAuth2RegisteredClientMetadata metadata) {
        Set<String> registeredScopes = parseScopeCollection(registeredClient.getScopes(), "CORE AGENT registered scopes");
        Set<String> metadataScopes = parseScopeCsv(metadata.scopes(), "CORE AGENT metadata scopes");
        if (!registeredScopes.equals(metadataScopes)) {
            throw invalid("CORE AGENT registered and metadata scopes are inconsistent");
        }
        return registeredScopes;
    }

    private static void verifyAutoApproveIsClientBound(Set<String> scopes, Set<String> autoApproveScopes) {
        Set<PermissionScopePattern> clientPatterns = parseScopePatterns(scopes, "CORE AGENT registered scopes");
        for (PermissionScopePattern autoApprovePattern : parseScopePatterns(autoApproveScopes,
                "CORE AGENT auto-approve scopes")) {
            boolean isBound = clientPatterns.stream().anyMatch(clientPattern -> clientPattern.meet(autoApprovePattern)
                    .filter(autoApprovePattern::equals).isPresent());
            if (!isBound) {
                throw invalid("CORE AGENT auto-approve scope is outside the client scopes");
            }
        }
    }

    private static void verifyAllowedIps(OAuth2RegisteredClientMetadata metadata) {
        if (!ALLOWED_IPS.equals(metadata.allowedIps())) {
            throw invalid("CORE AGENT allowed_ips must remain the fixed first-release value");
        }
    }

    private static void verifyClientSettings(ClientSettings clientSettings) {
        if (clientSettings == null || !clientSettings.isRequireProofKey()
                || !clientSettings.isRequireAuthorizationConsent()) {
            throw invalid("CORE AGENT must require PKCE and authorization consent");
        }
    }

    private static void verifyTokenSettings(TokenSettings tokenSettings) {
        if (tokenSettings == null
                || !ACCESS_TOKEN_TTL.equals(tokenSettings.getAccessTokenTimeToLive())
                || !REFRESH_TOKEN_TTL.equals(tokenSettings.getRefreshTokenTimeToLive())
                || !AUTHORIZATION_CODE_TTL.equals(tokenSettings.getAuthorizationCodeTimeToLive())
                || tokenSettings.isReuseRefreshTokens()) {
            throw invalid("CORE AGENT token settings do not match the fixed first-release policy");
        }
    }

    private static Set<String> parseScopeCsv(String value, String source) {
        Set<String> scopes = parseCsv(value, source);
        parseScopePatterns(scopes, source);
        return scopes;
    }

    private static Set<String> parseScopeCollection(Collection<String> values, String source) {
        if (values == null || values.isEmpty()) {
            throw invalid(source + " cannot be blank");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || !value.equals(value.trim()) || value.isEmpty() || !normalized.add(value)) {
                throw invalid(source + " contains an empty, duplicate, or non-canonical value");
            }
        }
        return parseScopeCsv(String.join(",", normalized), source);
    }

    private static Set<String> parseUriSet(Collection<String> values, String source) {
        if (values == null || values.isEmpty()) {
            throw invalid(source + " cannot be blank");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || !value.equals(value.trim()) || value.isEmpty() || !normalized.add(value)) {
                throw invalid(source + " contains an empty, duplicate, or non-canonical value");
            }
        }
        return Set.copyOf(normalized);
    }

    private static Set<PermissionScopePattern> parseScopePatterns(Collection<String> values, String source) {
        Set<PermissionScopePattern> patterns = new LinkedHashSet<>();
        for (String value : values) {
            try {
                PermissionScopePattern pattern = PermissionScopePattern.parse(value);
                if (!patterns.add(pattern)) {
                    throw invalid(source + " contains a duplicate scope pattern");
                }
            } catch (IllegalArgumentException exception) {
                throw invalid(source + " contains a malformed scope pattern");
            }
        }
        return Set.copyOf(patterns);
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

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException(message);
    }
}
