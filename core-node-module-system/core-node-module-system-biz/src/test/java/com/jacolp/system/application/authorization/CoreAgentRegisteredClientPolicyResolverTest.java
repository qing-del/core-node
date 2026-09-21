package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.OAuth2RegisteredClientMetadata;
import com.jacolp.system.application.port.out.OAuth2RegisteredClientMetadataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CoreAgentRegisteredClientPolicyResolverTest {

    private static final String CLIENT_ID = CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID;
    private static final String REGISTERED_ID = CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_REGISTERED_CLIENT_ID;
    private static final String REDIRECT_URI = CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_REDIRECT_URI;

    @Test
    void resolvesTheFixedPublicPkcePolicyIntoOnlyApplicationValues() {
        Fixture fixture = fixture(validRegisteredClient(), validMetadata());

        CoreAgentRegisteredClientPolicy policy = fixture.resolver.resolve(CLIENT_ID);

        assertThat(policy.registeredClientId()).isEqualTo(REGISTERED_ID);
        assertThat(policy.clientId()).isEqualTo(CLIENT_ID);
        assertThat(policy.redirectUri()).isEqualTo(REDIRECT_URI);
        assertThat(policy.scopes()).containsExactlyInAnyOrder("note:read", "note:write", "sys:read", "media:read");
        assertThat(policy.autoApproveScopes()).containsExactlyInAnyOrder("note:read", "sys:read");
        assertThat(policy.allowedIps()).isEqualTo("0.0.0.0/0");
        assertThat(policy.accessTokenTimeToLive()).isEqualTo(Duration.ofHours(1));
        assertThat(policy.refreshTokenTimeToLive()).isEqualTo(Duration.ofHours(24));
        assertThat(policy.authorizationCodeTimeToLive()).isEqualTo(Duration.ofMinutes(10));
        assertThat(policy.getClass().getRecordComponents()).allSatisfy(component ->
                assertThat(component.getType().getPackageName()).doesNotContain("infrastructure"));
        verifyLookup(fixture);
    }

    @Test
    void unsupportedClientAndMissingSourcesFailClosed() {
        RegisteredClientRepository clients = mock(RegisteredClientRepository.class);
        OAuth2RegisteredClientMetadataRepository metadata = mock(OAuth2RegisteredClientMetadataRepository.class);
        CoreAgentRegisteredClientPolicyResolver resolver = new CoreAgentRegisteredClientPolicyResolver(clients, metadata);

        assertThatIllegalStateException().isThrownBy(() -> resolver.resolve("user"));
        verifyNoInteractions(clients, metadata);

        Fixture missingClient = fixture(null, validMetadata());
        assertThatIllegalStateException().isThrownBy(() -> missingClient.resolver.resolve(CLIENT_ID));
        verify(missingClient.clients).findByClientId(CLIENT_ID);
        verifyNoMoreInteractions(missingClient.clients);
        verifyNoInteractions(missingClient.metadata);

        Fixture missingMetadata = fixture(validRegisteredClient(), null);
        assertRejected(missingMetadata);
        verifyLookup(missingMetadata);
    }

    @Test
    void identityStatusAndSecretPollutionFailClosed() {
        assertRejected(fixture(registeredClient("different-id", CLIENT_ID, null, noneMethods(), validGrants(),
                validRedirects(), validScopes(), validClientSettings(), validTokenSettings()), validMetadata()));
        assertRejected(fixture(registeredClient(REGISTERED_ID, "other", null, noneMethods(), validGrants(),
                validRedirects(), validScopes(), validClientSettings(), validTokenSettings()), validMetadata()));
        assertRejected(fixture(validRegisteredClient(), metadata(REGISTERED_ID, CLIENT_ID, "disabled", null,
                "none", validGrantCsv(), REDIRECT_URI, validScopeCsv(), validAutoApproveCsv(), "0.0.0.0/0")));
        assertRejected(fixture(validRegisteredClient(), metadata("wrong-id", CLIENT_ID, "active", null,
                "none", validGrantCsv(), REDIRECT_URI, validScopeCsv(), validAutoApproveCsv(), "0.0.0.0/0")));
        assertRejected(fixture(registeredClient(REGISTERED_ID, CLIENT_ID, "secret", noneMethods(), validGrants(),
                validRedirects(), validScopes(), validClientSettings(), validTokenSettings()), validMetadata()));
        assertRejected(fixture(validRegisteredClient(), metadata(REGISTERED_ID, CLIENT_ID, "active", "secret",
                "none", validGrantCsv(), REDIRECT_URI, validScopeCsv(), validAutoApproveCsv(), "0.0.0.0/0")));
    }

    @Test
    void authenticationMethodsAndGrantsMustBeTheExactTwoGrantPublicCatalogue() {
        assertRejected(fixture(registeredClient(REGISTERED_ID, CLIENT_ID, null, Set.of(ClientAuthenticationMethod.NONE,
                new ClientAuthenticationMethod("client_secret_basic")), validGrants(), validRedirects(), validScopes(),
                validClientSettings(), validTokenSettings()), validMetadata()));
        assertRejected(fixture(validRegisteredClient(), metadata(REGISTERED_ID, CLIENT_ID, "active", null,
                "none,client_secret_basic", validGrantCsv(), REDIRECT_URI, validScopeCsv(), validAutoApproveCsv(), "0.0.0.0/0")));
        assertRejected(fixture(registeredClient(REGISTERED_ID, CLIENT_ID, null, noneMethods(), Set.of(
                AuthorizationGrantType.AUTHORIZATION_CODE), validRedirects(), validScopes(), validClientSettings(),
                validTokenSettings()), validMetadata()));
        assertRejected(fixture(registeredClient(REGISTERED_ID, CLIENT_ID, null, noneMethods(), Set.of(
                AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN,
                AuthorizationGrantType.DEVICE_CODE), validRedirects(), validScopes(), validClientSettings(),
                validTokenSettings()), validMetadata()));
        assertRejected(fixture(validRegisteredClient(), metadata(REGISTERED_ID, CLIENT_ID, "active", null,
                "none", "authorization_code,refresh_token,device_code", REDIRECT_URI, validScopeCsv(),
                validAutoApproveCsv(), "0.0.0.0/0")));
    }

    @Test
    void redirectUrisMustBeTheOneExactRegisteredCallback() {
        assertRejected(fixture(registeredClient(REGISTERED_ID, CLIENT_ID, null, noneMethods(), validGrants(),
                Set.of(REDIRECT_URI, "http://127.0.0.1:9091/oauth/callback"), validScopes(), validClientSettings(),
                validTokenSettings()), validMetadata()));
        assertRejected(fixture(validRegisteredClient(), metadata(REGISTERED_ID, CLIENT_ID, "active", null,
                "none", validGrantCsv(), REDIRECT_URI + ",http://127.0.0.1:9091/oauth/callback", validScopeCsv(),
                validAutoApproveCsv(), "0.0.0.0/0")));
        assertRejected(fixture(validRegisteredClient(), metadata(REGISTERED_ID, CLIENT_ID, "active", null,
                "none", validGrantCsv(), "  ", validScopeCsv(), validAutoApproveCsv(), "0.0.0.0/0")));
    }

    @Test
    void scopesAndAutoApproveMustBeStrictPermissionPatternsAndConsistent() {
        assertRejected(fixture(registeredClient(REGISTERED_ID, CLIENT_ID, null, noneMethods(), validGrants(),
                validRedirects(), Set.of("note:read", "note:write", "sys:read"), validClientSettings(),
                validTokenSettings()), validMetadata()));
        assertRejected(fixture(validRegisteredClient(), metadata(REGISTERED_ID, CLIENT_ID, "active", null,
                "none", validGrantCsv(), REDIRECT_URI, "note:read,note:read", validAutoApproveCsv(), "0.0.0.0/0")));
        assertRejected(fixture(validRegisteredClient(), metadata(REGISTERED_ID, CLIENT_ID, "active", null,
                "none", validGrantCsv(), REDIRECT_URI, "note:read,note:read:more", validAutoApproveCsv(), "0.0.0.0/0")));
        assertRejected(fixture(validRegisteredClient(), metadata(REGISTERED_ID, CLIENT_ID, "active", null,
                "none", validGrantCsv(), REDIRECT_URI, validScopeCsv(), "*:manage", "0.0.0.0/0")));
        assertRejected(fixture(validRegisteredClient(), metadata(REGISTERED_ID, CLIENT_ID, "active", null,
                "none", validGrantCsv(), REDIRECT_URI, validScopeCsv(), "note:read,note:read:more", "0.0.0.0/0")));
    }

    @Test
    void pkceConsentTokenTtlsRotationAndAllowedIpsMustMatchExactly() {
        assertRejected(fixture(validRegisteredClientWith(ClientSettings.builder().requireProofKey(false)
                .requireAuthorizationConsent(true).build(), validTokenSettings()), validMetadata()));
        assertRejected(fixture(validRegisteredClientWith(ClientSettings.builder().requireProofKey(true)
                .requireAuthorizationConsent(false).build(), validTokenSettings()), validMetadata()));
        assertRejected(fixture(validRegisteredClientWith(validClientSettings(), tokenSettings(Duration.ofHours(2),
                Duration.ofHours(24), Duration.ofMinutes(10), false)), validMetadata()));
        assertRejected(fixture(validRegisteredClientWith(validClientSettings(), tokenSettings(Duration.ofHours(1),
                Duration.ofHours(48), Duration.ofMinutes(10), false)), validMetadata()));
        assertRejected(fixture(validRegisteredClientWith(validClientSettings(), tokenSettings(Duration.ofHours(1),
                Duration.ofHours(24), Duration.ofMinutes(11), false)), validMetadata()));
        assertRejected(fixture(validRegisteredClientWith(validClientSettings(), tokenSettings(Duration.ofHours(1),
                Duration.ofHours(24), Duration.ofMinutes(10), true)), validMetadata()));
        assertRejected(fixture(validRegisteredClient(), metadata(REGISTERED_ID, CLIENT_ID, "active", null,
                "none", validGrantCsv(), REDIRECT_URI, validScopeCsv(), validAutoApproveCsv(), "10.0.0.0/8")));
    }

    private static void assertRejected(Fixture fixture) {
        assertThatIllegalStateException().isThrownBy(() -> fixture.resolver.resolve(CLIENT_ID));
        verifyLookup(fixture);
    }

    private static Fixture fixture(RegisteredClient registeredClient, OAuth2RegisteredClientMetadata clientMetadata) {
        RegisteredClientRepository clients = mock(RegisteredClientRepository.class);
        OAuth2RegisteredClientMetadataRepository metadata = mock(OAuth2RegisteredClientMetadataRepository.class);
        when(clients.findByClientId(CLIENT_ID)).thenReturn(registeredClient);
        if (registeredClient != null) {
            when(metadata.findByClientId(CLIENT_ID)).thenReturn(Optional.ofNullable(clientMetadata));
        }
        return new Fixture(clients, metadata, new CoreAgentRegisteredClientPolicyResolver(clients, metadata));
    }

    private static void verifyLookup(Fixture fixture) {
        verify(fixture.clients).findByClientId(CLIENT_ID);
        verify(fixture.metadata).findByClientId(CLIENT_ID);
        verifyNoMoreInteractions(fixture.clients, fixture.metadata);
    }

    private static RegisteredClient validRegisteredClient() {
        return registeredClient(REGISTERED_ID, CLIENT_ID, null, noneMethods(), validGrants(), validRedirects(),
                validScopes(), validClientSettings(), validTokenSettings());
    }

    private static RegisteredClient validRegisteredClientWith(ClientSettings settings, TokenSettings tokenSettings) {
        return registeredClient(REGISTERED_ID, CLIENT_ID, null, noneMethods(), validGrants(), validRedirects(),
                validScopes(), settings, tokenSettings);
    }

    private static RegisteredClient registeredClient(String id, String clientId, String secret,
                                                     Set<ClientAuthenticationMethod> authenticationMethods,
                                                     Set<AuthorizationGrantType> grantTypes, Set<String> redirectUris,
                                                     Set<String> scopes, ClientSettings clientSettings,
                                                     TokenSettings tokenSettings) {
        RegisteredClient.Builder builder = RegisteredClient.withId(id)
                .clientId(clientId)
                .clientName("CORE AGENT")
                .clientAuthenticationMethods(methods -> methods.addAll(authenticationMethods))
                .authorizationGrantTypes(grants -> grants.addAll(grantTypes))
                .redirectUris(uris -> uris.addAll(redirectUris))
                .scopes(clientScopes -> clientScopes.addAll(scopes))
                .clientSettings(clientSettings)
                .tokenSettings(tokenSettings);
        if (secret != null) {
            builder.clientSecret(secret);
        }
        return builder.build();
    }

    private static OAuth2RegisteredClientMetadata validMetadata() {
        return metadata(REGISTERED_ID, CLIENT_ID, "active", null, "none", validGrantCsv(), REDIRECT_URI,
                validScopeCsv(), validAutoApproveCsv(), "0.0.0.0/0");
    }

    private static OAuth2RegisteredClientMetadata metadata(String id, String clientId, String status, String secret,
                                                            String authenticationMethods, String grants,
                                                            String redirectUris, String scopes, String autoApprove,
                                                            String allowedIps) {
        return new OAuth2RegisteredClientMetadata(id, clientId, LocalDateTime.of(2026, 8, 11, 1, 2, 3), secret, null,
                "CORE AGENT", authenticationMethods, grants, redirectUris, null, scopes, "{}", "{}", autoApprove,
                status, allowedIps);
    }

    private static Set<ClientAuthenticationMethod> noneMethods() {
        return Set.of(ClientAuthenticationMethod.NONE);
    }

    private static Set<AuthorizationGrantType> validGrants() {
        return Set.of(AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN);
    }

    private static Set<String> validRedirects() {
        return Set.of(REDIRECT_URI);
    }

    private static Set<String> validScopes() {
        return Set.of("note:read", "note:write", "sys:read", "media:read");
    }

    private static String validGrantCsv() {
        return "authorization_code,refresh_token";
    }

    private static String validScopeCsv() {
        return "note:read,note:write,sys:read,media:read";
    }

    private static String validAutoApproveCsv() {
        return "note:read,sys:read";
    }

    private static ClientSettings validClientSettings() {
        return ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true).build();
    }

    private static TokenSettings validTokenSettings() {
        return tokenSettings(Duration.ofHours(1), Duration.ofHours(24), Duration.ofMinutes(10), false);
    }

    private static TokenSettings tokenSettings(Duration accessTtl, Duration refreshTtl, Duration authCodeTtl,
                                               boolean reuseRefreshTokens) {
        return TokenSettings.builder().accessTokenTimeToLive(accessTtl).refreshTokenTimeToLive(refreshTtl)
                .authorizationCodeTimeToLive(authCodeTtl).reuseRefreshTokens(reuseRefreshTokens).build();
    }

    private record Fixture(RegisteredClientRepository clients, OAuth2RegisteredClientMetadataRepository metadata,
                           CoreAgentRegisteredClientPolicyResolver resolver) {
    }
}
