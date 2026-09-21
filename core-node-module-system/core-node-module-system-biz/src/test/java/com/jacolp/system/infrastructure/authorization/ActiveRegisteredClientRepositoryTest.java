package com.jacolp.system.infrastructure.authorization;

import com.jacolp.system.application.authorization.model.OAuth2RegisteredClientMetadata;
import com.jacolp.system.application.port.out.OAuth2RegisteredClientMetadataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ActiveRegisteredClientRepositoryTest {

    private static final String USER_ID = "e7cf5b30-8e43-4db2-bc53-000000000001";
    private static final String USER_CLIENT_ID = "user";
    private static final String CORE_AGENT_ID = "e7cf5b30-8e43-4db2-bc53-000000000003";
    private static final String CORE_AGENT_CLIENT_ID = "core_agent";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(OAuth2RegisteredClientMetadataRepository.class,
                    () -> mock(OAuth2RegisteredClientMetadataRepository.class))
            .withBean(JdbcOperations.class, () -> mock(JdbcOperations.class))
            .withBean(ActiveRegisteredClientRepository.class);

    @Test
    void springUsesTheProductionConstructorAndRegistersOnlyOneSasRepositoryBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RegisteredClientRepository.class);
            assertThat(context.getBean(RegisteredClientRepository.class))
                    .isInstanceOf(ActiveRegisteredClientRepository.class);
        });
    }

    @Test
    void activeInternalClientReturnsTheCompleteSasMappingWithOneLookupPerRepository() {
        OAuth2RegisteredClientMetadataRepository metadataRepository = mock(OAuth2RegisteredClientMetadataRepository.class);
        RegisteredClientRepository sasDelegate = mock(RegisteredClientRepository.class);
        ActiveRegisteredClientRepository repository = new ActiveRegisteredClientRepository(metadataRepository, sasDelegate);
        RegisteredClient expected = activeUser();
        when(metadataRepository.findByClientId(USER_CLIENT_ID)).thenReturn(Optional.of(metadata("active")));
        when(sasDelegate.findByClientId(USER_CLIENT_ID)).thenReturn(expected);

        RegisteredClient actual = repository.findByClientId(USER_CLIENT_ID);

        assertThat(actual).isSameAs(expected);
        assertThat(actual.getClientAuthenticationMethods())
                .containsExactly(new ClientAuthenticationMethod("internal"));
        assertThat(actual.getAuthorizationGrantTypes())
                .containsExactlyInAnyOrder(new AuthorizationGrantType("password"),
                        new AuthorizationGrantType("email-code"), AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(actual.getScopes()).containsExactlyInAnyOrder("*:read", "*:write");
        assertThat(actual.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofHours(3));
        assertThat(actual.getTokenSettings().getRefreshTokenTimeToLive()).isEqualTo(Duration.ofHours(72));
        assertThat(actual.getTokenSettings().isReuseRefreshTokens()).isFalse();
        verify(metadataRepository).findByClientId(USER_CLIENT_ID);
        verify(sasDelegate).findByClientId(USER_CLIENT_ID);
        verifyNoMoreInteractions(metadataRepository, sasDelegate);
    }

    @Test
    void activeCoreAgentReturnsThePublicPkceSasMappingWithOneLookupPerRepository() {
        OAuth2RegisteredClientMetadataRepository metadataRepository = mock(OAuth2RegisteredClientMetadataRepository.class);
        RegisteredClientRepository sasDelegate = mock(RegisteredClientRepository.class);
        ActiveRegisteredClientRepository repository = new ActiveRegisteredClientRepository(metadataRepository, sasDelegate);
        RegisteredClient expected = activeCoreAgent();
        when(metadataRepository.findByClientId(CORE_AGENT_CLIENT_ID)).thenReturn(Optional.of(coreAgentMetadata("active")));
        when(sasDelegate.findByClientId(CORE_AGENT_CLIENT_ID)).thenReturn(expected);

        RegisteredClient actual = repository.findByClientId(CORE_AGENT_CLIENT_ID);

        assertThat(actual).isSameAs(expected);
        assertThat(actual.getClientSecret()).isNull();
        assertThat(actual.getClientAuthenticationMethods()).containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(actual.getAuthorizationGrantTypes()).containsExactlyInAnyOrder(
                AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(actual.getRedirectUris()).containsExactly("http://127.0.0.1:9090/oauth/callback");
        assertThat(actual.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(actual.getClientSettings().isRequireAuthorizationConsent()).isTrue();
        assertThat(actual.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofHours(1));
        assertThat(actual.getTokenSettings().getRefreshTokenTimeToLive()).isEqualTo(Duration.ofHours(24));
        assertThat(actual.getTokenSettings().isReuseRefreshTokens()).isFalse();
        verify(metadataRepository).findByClientId(CORE_AGENT_CLIENT_ID);
        verify(sasDelegate).findByClientId(CORE_AGENT_CLIENT_ID);
        verifyNoMoreInteractions(metadataRepository, sasDelegate);
    }

    @Test
    void disabledClientDoesNotReachSasDelegate() {
        OAuth2RegisteredClientMetadataRepository metadataRepository = mock(OAuth2RegisteredClientMetadataRepository.class);
        RegisteredClientRepository sasDelegate = mock(RegisteredClientRepository.class);
        ActiveRegisteredClientRepository repository = new ActiveRegisteredClientRepository(metadataRepository, sasDelegate);
        when(metadataRepository.findByClientId("core_agent")).thenReturn(Optional.of(metadata("disabled")));

        assertThat(repository.findByClientId("core_agent")).isNull();

        verify(metadataRepository).findByClientId("core_agent");
        verifyNoMoreInteractions(metadataRepository);
        verifyNoInteractions(sasDelegate);
    }

    @Test
    void statusMustMatchLowercaseActiveExactly() {
        OAuth2RegisteredClientMetadataRepository metadataRepository = mock(OAuth2RegisteredClientMetadataRepository.class);
        RegisteredClientRepository sasDelegate = mock(RegisteredClientRepository.class);
        ActiveRegisteredClientRepository repository = new ActiveRegisteredClientRepository(metadataRepository, sasDelegate);
        when(metadataRepository.findByClientId(USER_CLIENT_ID)).thenReturn(Optional.of(metadata("ACTIVE")));

        assertThat(repository.findByClientId(USER_CLIENT_ID)).isNull();

        verify(metadataRepository).findByClientId(USER_CLIENT_ID);
        verifyNoMoreInteractions(metadataRepository);
        verifyNoInteractions(sasDelegate);
    }

    @Test
    void missingOrMismatchedSasClientFailsClosedAfterExactlyOneLookupEach() {
        OAuth2RegisteredClientMetadataRepository metadataRepository = mock(OAuth2RegisteredClientMetadataRepository.class);
        RegisteredClientRepository sasDelegate = mock(RegisteredClientRepository.class);
        ActiveRegisteredClientRepository repository = new ActiveRegisteredClientRepository(metadataRepository, sasDelegate);
        when(metadataRepository.findById(USER_ID)).thenReturn(Optional.of(metadata("active")));
        when(sasDelegate.findById(USER_ID)).thenReturn(null);

        assertThatIllegalStateException().isThrownBy(() -> repository.findById(USER_ID));

        verify(metadataRepository).findById(USER_ID);
        verify(sasDelegate).findById(USER_ID);
        verifyNoMoreInteractions(metadataRepository, sasDelegate);
    }

    @Test
    void mismatchedSasClientFailsClosed() {
        OAuth2RegisteredClientMetadataRepository metadataRepository = mock(OAuth2RegisteredClientMetadataRepository.class);
        RegisteredClientRepository sasDelegate = mock(RegisteredClientRepository.class);
        ActiveRegisteredClientRepository repository = new ActiveRegisteredClientRepository(metadataRepository, sasDelegate);
        when(metadataRepository.findById(USER_ID)).thenReturn(Optional.of(metadata("active")));
        when(sasDelegate.findById(USER_ID)).thenReturn(RegisteredClient.withId(USER_ID)
                .clientId("another-client").clientName("mismatch")
                .clientAuthenticationMethod(new ClientAuthenticationMethod("internal"))
                .authorizationGrantType(new AuthorizationGrantType("password")).build());

        assertThatIllegalStateException().isThrownBy(() -> repository.findById(USER_ID));

        verify(metadataRepository).findById(USER_ID);
        verify(sasDelegate).findById(USER_ID);
        verifyNoMoreInteractions(metadataRepository, sasDelegate);
    }

    @Test
    void saveIsRejectedWithoutReadingOrWritingEitherRepository() {
        OAuth2RegisteredClientMetadataRepository metadataRepository = mock(OAuth2RegisteredClientMetadataRepository.class);
        RegisteredClientRepository sasDelegate = mock(RegisteredClientRepository.class);
        ActiveRegisteredClientRepository repository = new ActiveRegisteredClientRepository(metadataRepository, sasDelegate);

        assertThatThrownBy(() -> repository.save(activeUser()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("management");

        verifyNoInteractions(metadataRepository, sasDelegate);
    }

    private static RegisteredClient activeUser() {
        return RegisteredClient.withId(USER_ID)
                .clientId(USER_CLIENT_ID)
                .clientIdIssuedAt(Instant.parse("2026-08-10T00:00:00Z"))
                .clientName("CORE NODE User Client")
                .clientAuthenticationMethod(new ClientAuthenticationMethod("internal"))
                .authorizationGrantType(new AuthorizationGrantType("password"))
                .authorizationGrantType(new AuthorizationGrantType("email-code"))
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope("*:read")
                .scope("*:write")
                .clientSettings(ClientSettings.builder().requireProofKey(false).requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(3))
                        .refreshTokenTimeToLive(Duration.ofHours(72))
                        .reuseRefreshTokens(false)
                        .build())
                .build();
    }

    private static RegisteredClient activeCoreAgent() {
        return RegisteredClient.withId(CORE_AGENT_ID)
                .clientId(CORE_AGENT_CLIENT_ID)
                .clientIdIssuedAt(Instant.parse("2026-08-10T00:00:00Z"))
                .clientName("CORE AGENT")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://127.0.0.1:9090/oauth/callback")
                .scope("note:read")
                .scope("note:write")
                .scope("sys:read")
                .scope("media:read")
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true).build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .refreshTokenTimeToLive(Duration.ofHours(24))
                        .reuseRefreshTokens(false)
                        .build())
                .build();
    }

    private static OAuth2RegisteredClientMetadata metadata(String status) {
        return new OAuth2RegisteredClientMetadata(USER_ID, USER_CLIENT_ID,
                LocalDateTime.of(2026, 8, 10, 8, 0), null, null, "CORE NODE User Client",
                "internal", "password,email-code,refresh_token", null, null, "*:read,*:write",
                "{}", "{}", "*:read,*:write", status, "0.0.0.0/0");
    }

    private static OAuth2RegisteredClientMetadata coreAgentMetadata(String status) {
        return new OAuth2RegisteredClientMetadata(CORE_AGENT_ID, CORE_AGENT_CLIENT_ID,
                LocalDateTime.of(2026, 8, 10, 8, 0), null, null, "CORE AGENT",
                "none", "authorization_code,refresh_token", "http://127.0.0.1:9090/oauth/callback", null,
                "note:read,note:write,sys:read,media:read", "{}", "{}", "note:read,sys:read", status,
                "0.0.0.0/0");
    }
}
