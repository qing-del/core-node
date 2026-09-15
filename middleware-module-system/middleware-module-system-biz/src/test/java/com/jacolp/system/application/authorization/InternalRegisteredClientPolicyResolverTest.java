package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.InternalRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.OAuth2RegisteredClientMetadata;
import com.jacolp.system.application.port.out.OAuth2RegisteredClientMetadataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class InternalRegisteredClientPolicyResolverTest {

    @Test
    void userAndAdminResolveTheFixedInternalPolicy() {
        Fixture user = fixture("user", registeredClient("user", null, internalMethods(), validGrants(), validSettings()),
                metadata("user"));
        Fixture admin = fixture("admin", registeredClient("admin", null, internalMethods(), validGrants(), validSettings()),
                metadata("admin"));

        assertThat(user.resolver.resolve("user", "password")).isEqualTo(policy("user", "password"));
        assertThat(admin.resolver.resolve("admin", "email-code")).isEqualTo(policy("admin", "email-code"));
        verifyLookup(user, "user");
        verifyLookup(admin, "admin");
    }

    @Test
    void refreshUsesTheDedicatedTechnicalPolicyResolutionPath() {
        Fixture user = fixture("user", registeredClient("user", null, internalMethods(), validGrants(), validSettings()),
                metadata("user"));

        assertThat(user.resolver.resolveRefresh("user")).isEqualTo(policy("user", "refresh_token"));
        verifyLookup(user, "user");
    }

    @Test
    void coreAgentAndUnsupportedGrantsAreRejectedWithIdentifiableReasonsBeforeAnyRead() {
        RegisteredClientRepository clients = mock(RegisteredClientRepository.class);
        OAuth2RegisteredClientMetadataRepository metadata = mock(OAuth2RegisteredClientMetadataRepository.class);
        InternalRegisteredClientPolicyResolver resolver = new InternalRegisteredClientPolicyResolver(clients, metadata);

        assertThatThrownBy(() -> resolver.resolve("core_agent", "authorization_code"))
                .isInstanceOf(InternalAccountAuthenticationRejectedException.class)
                .hasMessage("不支持当前登录客户端");
        assertThatThrownBy(() -> resolver.resolve("user", "refresh_token"))
                .isInstanceOf(InternalAccountAuthenticationRejectedException.class)
                .hasMessage("不支持当前登录方式");

        verifyNoInteractions(clients, metadata);
    }

    @Test
    void missingDisabledOrInconsistentMetadataFailsClosed() {
        Fixture missingRegisteredClient = fixture("user", null, metadata("user"));
        assertThatIllegalStateException().isThrownBy(() -> missingRegisteredClient.resolver.resolve("user", "password"));
        verify(missingRegisteredClient.clients).findByClientId("user");
        verifyNoMoreInteractions(missingRegisteredClient.clients);
        verifyNoInteractions(missingRegisteredClient.metadata);

        Fixture disabledMetadata = fixture("user", registeredClient("user", null, internalMethods(), validGrants(), validSettings()),
                metadata("user", "disabled", null, "internal", validGrantCsv(), "*:read,*:write", "*:read"));
        assertThatIllegalStateException().isThrownBy(() -> disabledMetadata.resolver.resolve("user", "password"));
        verifyLookup(disabledMetadata, "user");

        Fixture mismatched = fixture("user", registeredClient("user", null, internalMethods(), validGrants(), validSettings()),
                metadata("another"));
        assertThatIllegalStateException().isThrownBy(() -> mismatched.resolver.resolve("user", "password"));
        verifyLookup(mismatched, "user");
    }

    @Test
    void secretAndAuthenticationMethodViolationsFailClosed() {
        Fixture secret = fixture("user", registeredClient("user", "secret", internalMethods(), validGrants(), validSettings()),
                metadata("user"));
        assertThatIllegalStateException().isThrownBy(() -> secret.resolver.resolve("user", "password"));
        verifyLookup(secret, "user");

        Fixture metadataSecret = fixture("user", registeredClient("user", null, internalMethods(), validGrants(), validSettings()),
                metadata("user", "active", "secret", "internal", validGrantCsv(), "*:read,*:write", "*:read"));
        assertThatIllegalStateException().isThrownBy(() -> metadataSecret.resolver.resolve("user", "password"));
        verifyLookup(metadataSecret, "user");

        Fixture authenticationMethod = fixture("user", registeredClient("user", null,
                Set.of(ClientAuthenticationMethod.NONE), validGrants(), validSettings()), metadata("user"));
        assertThatIllegalStateException().isThrownBy(() -> authenticationMethod.resolver.resolve("user", "password"));
        verifyLookup(authenticationMethod, "user");

        Fixture metadataAuthenticationMethod = fixture("user", registeredClient("user", null, internalMethods(), validGrants(), validSettings()),
                metadata("user", "active", null, "internal,none", validGrantCsv(), "*:read,*:write", "*:read"));
        assertThatIllegalStateException().isThrownBy(() -> metadataAuthenticationMethod.resolver.resolve("user", "password"));
        verifyLookup(metadataAuthenticationMethod, "user");
    }

    @Test
    void loginAndRefreshGrantsMustBeRegisteredOnBothViews() {
        Fixture missingRequestedGrant = fixture("user", registeredClient("user", null, internalMethods(),
                Set.of(AuthorizationGrantType.REFRESH_TOKEN), validSettings()), metadata("user"));
        assertThatIllegalStateException().isThrownBy(() -> missingRequestedGrant.resolver.resolve("user", "password"));
        verifyLookup(missingRequestedGrant, "user");

        Fixture missingRefreshGrant = fixture("user", registeredClient("user", null, internalMethods(),
                Set.of(new AuthorizationGrantType("password")), validSettings()), metadata("user"));
        assertThatIllegalStateException().isThrownBy(() -> missingRefreshGrant.resolver.resolve("user", "password"));
        verifyLookup(missingRefreshGrant, "user");

        Fixture metadataMissingRefresh = fixture("user", registeredClient("user", null, internalMethods(), validGrants(), validSettings()),
                metadata("user", "active", null, "internal", "password,email-code", "*:read,*:write", "*:read"));
        assertThatIllegalStateException().isThrownBy(() -> metadataMissingRefresh.resolver.resolve("user", "password"));
        verifyLookup(metadataMissingRefresh, "user");

        Fixture extraRegisteredGrant = fixture("user", registeredClient("user", null, internalMethods(),
                Set.of(new AuthorizationGrantType("password"), new AuthorizationGrantType("email-code"),
                        AuthorizationGrantType.REFRESH_TOKEN, AuthorizationGrantType.CLIENT_CREDENTIALS), validSettings()),
                metadata("user"));
        assertThatIllegalStateException().isThrownBy(() -> extraRegisteredGrant.resolver.resolveRefresh("user"));
        verifyLookup(extraRegisteredGrant, "user");

        Fixture extraMetadataGrant = fixture("user", registeredClient("user", null, internalMethods(), validGrants(), validSettings()),
                metadata("user", "active", null, "internal", "password,email-code,refresh_token,client_credentials",
                        "*:read,*:write", "*:read"));
        assertThatIllegalStateException().isThrownBy(() -> extraMetadataGrant.resolver.resolveRefresh("user"));
        verifyLookup(extraMetadataGrant, "user");
    }

    @Test
    void tokenSettingsMustHavePositiveTtlsAndRefreshRotation() {
        TokenSettings invalidTtl = mock(TokenSettings.class);
        when(invalidTtl.getAccessTokenTimeToLive()).thenReturn(Duration.ZERO);
        when(invalidTtl.getRefreshTokenTimeToLive()).thenReturn(Duration.ofHours(72));
        when(invalidTtl.isReuseRefreshTokens()).thenReturn(false);
        Fixture zeroAccessTtl = fixture("user", registeredClient("user", null, internalMethods(), validGrants(), invalidTtl),
                metadata("user"));
        assertThatIllegalStateException().isThrownBy(() -> zeroAccessTtl.resolver.resolve("user", "password"));
        verifyLookup(zeroAccessTtl, "user");

        TokenSettings reusedRefresh = mock(TokenSettings.class);
        when(reusedRefresh.getAccessTokenTimeToLive()).thenReturn(Duration.ofHours(3));
        when(reusedRefresh.getRefreshTokenTimeToLive()).thenReturn(Duration.ofHours(72));
        when(reusedRefresh.isReuseRefreshTokens()).thenReturn(true);
        Fixture rotationDisabled = fixture("user", registeredClient("user", null, internalMethods(), validGrants(), reusedRefresh),
                metadata("user"));
        assertThatIllegalStateException().isThrownBy(() -> rotationDisabled.resolver.resolve("user", "password"));
        verifyLookup(rotationDisabled, "user");
    }

    @Test
    void metadataCsvAndAllowedIpsMustBeStrictAndNonEmpty() {
        Fixture duplicateScope = fixture("user", registeredClient("user", null, internalMethods(), validGrants(), validSettings()),
                metadata("user", "active", null, "internal", validGrantCsv(), "*:read, *:read ", "*:read"));
        assertThatIllegalStateException().isThrownBy(() -> duplicateScope.resolver.resolve("user", "password"));
        verifyLookup(duplicateScope, "user");

        Fixture malformedAutoApprove = fixture("user", registeredClient("user", null, internalMethods(), validGrants(), validSettings()),
                metadata("user", "active", null, "internal", validGrantCsv(), "*:read", "note:read:more"));
        assertThatIllegalStateException().isThrownBy(() -> malformedAutoApprove.resolver.resolve("user", "password"));
        verifyLookup(malformedAutoApprove, "user");

        Fixture blankAllowedIps = fixture("user", registeredClient("user", null, internalMethods(), validGrants(), validSettings()),
                metadata("user", "active", null, "internal", validGrantCsv(), "*:read", "*:read", "  "));
        assertThatIllegalStateException().isThrownBy(() -> blankAllowedIps.resolver.resolve("user", "password"));
        verifyLookup(blankAllowedIps, "user");
    }

    private static Fixture fixture(String clientId, RegisteredClient registeredClient,
                                   OAuth2RegisteredClientMetadata clientMetadata) {
        RegisteredClientRepository clients = mock(RegisteredClientRepository.class);
        OAuth2RegisteredClientMetadataRepository metadata = mock(OAuth2RegisteredClientMetadataRepository.class);
        when(clients.findByClientId(clientId)).thenReturn(registeredClient);
        if (registeredClient != null) {
            when(metadata.findByClientId(clientId)).thenReturn(Optional.of(clientMetadata));
        }
        return new Fixture(clients, metadata, new InternalRegisteredClientPolicyResolver(clients, metadata));
    }

    private static void verifyLookup(Fixture fixture, String clientId) {
        verify(fixture.clients).findByClientId(clientId);
        verify(fixture.metadata).findByClientId(clientId);
        verifyNoMoreInteractions(fixture.clients, fixture.metadata);
    }

    private static RegisteredClient registeredClient(String clientId, String secret,
                                                     Set<ClientAuthenticationMethod> authenticationMethods,
                                                     Set<AuthorizationGrantType> grantTypes,
                                                     TokenSettings tokenSettings) {
        RegisteredClient.Builder builder = RegisteredClient.withId(clientId + "-id")
                .clientId(clientId)
                .clientName(clientId + " client")
                .clientAuthenticationMethods(methods -> methods.addAll(authenticationMethods))
                .authorizationGrantTypes(grants -> grants.addAll(grantTypes))
                .scope("*:read")
                .tokenSettings(tokenSettings);
        if (secret != null) {
            builder.clientSecret(secret);
        }
        return builder.build();
    }

    private static Set<ClientAuthenticationMethod> internalMethods() {
        return Set.of(new ClientAuthenticationMethod("internal"));
    }

    private static Set<AuthorizationGrantType> validGrants() {
        return Set.of(new AuthorizationGrantType("password"), new AuthorizationGrantType("email-code"),
                AuthorizationGrantType.REFRESH_TOKEN);
    }

    private static TokenSettings validSettings() {
        return TokenSettings.builder().accessTokenTimeToLive(Duration.ofHours(3))
                .refreshTokenTimeToLive(Duration.ofHours(72)).reuseRefreshTokens(false).build();
    }

    private static OAuth2RegisteredClientMetadata metadata(String clientId) {
        return metadata(clientId, "active", null, "internal", validGrantCsv(), "*:read,*:write", "*:read");
    }

    private static OAuth2RegisteredClientMetadata metadata(String clientId, String status, String secret,
                                                            String authenticationMethods, String grants, String scopes,
                                                            String autoApprove) {
        return metadata(clientId, status, secret, authenticationMethods, grants, scopes, autoApprove,
                "0.0.0.0/0,::/0");
    }

    private static OAuth2RegisteredClientMetadata metadata(String clientId, String status, String secret,
                                                            String authenticationMethods, String grants, String scopes,
                                                            String autoApprove, String allowedIps) {
        return new OAuth2RegisteredClientMetadata(clientId + "-id", clientId,
                LocalDateTime.of(2026, 8, 10, 1, 2, 3), secret, null, clientId + " client", authenticationMethods,
                grants, null, null, scopes, "{}", "{}", autoApprove, status, allowedIps);
    }

    private static String validGrantCsv() {
        return "password,email-code,refresh_token";
    }

    private static InternalRegisteredClientPolicy policy(String clientId, String grantType) {
        return new InternalRegisteredClientPolicy(clientId + "-id", clientId, grantType,
                Set.of("*:read", "*:write"), Set.of("*:read"), "0.0.0.0/0,::/0",
                Duration.ofHours(3), Duration.ofHours(72));
    }

    private record Fixture(RegisteredClientRepository clients, OAuth2RegisteredClientMetadataRepository metadata,
                           InternalRegisteredClientPolicyResolver resolver) {
    }
}
