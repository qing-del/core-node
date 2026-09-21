package com.jacolp.system.infrastructure.authorization;

import com.jacolp.system.web.authorization.CoreAgentAuthorizationCodeRequestAuthenticationProvider;
import com.jacolp.system.web.authorization.CoreAgentAuthorizationCodeTokenAuthenticationConverter;
import com.jacolp.system.web.authorization.CoreAgentAuthorizationCodeTokenAuthenticationProvider;
import com.jacolp.system.web.authorization.CoreAgentAuthorizationConsentAuthenticationProvider;
import com.jacolp.system.web.authorization.CoreAgentAuthorizationEndpointAuthenticationConverter;
import com.jacolp.system.web.authorization.CoreAgentPublicClientAuthenticationConverter;
import com.jacolp.system.web.authorization.CoreAgentPublicClientAuthenticationProvider;
import com.jacolp.system.web.authorization.CoreAgentRefreshTokenAuthenticationConverter;
import com.jacolp.system.web.authorization.CoreAgentRefreshTokenAuthenticationProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.authentication.AuthenticationConverter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoreAgentAuthorizationServerConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConfigurationUnderTest.class);

    @Test
    void settingsFixOnlyAuthorizationTokenAndConsentPathsWithoutForgingAnIssuerOrLogoutEndpoint() {
        AuthorizationServerSettings settings = new CoreAgentAuthorizationServerConfiguration()
                .coreAgentAuthorizationServerSettings();

        assertThat(settings.getAuthorizationEndpoint()).isEqualTo("/oauth2/authorize");
        assertThat(settings.getTokenEndpoint()).isEqualTo("/oauth/token");
        assertThat(settings.getTokenRevocationEndpoint()).isNotEqualTo("/oauth/logout");
        assertThat(settings.getIssuer()).isNull();
        assertThat(CoreAgentAuthorizationServerConfiguration.CONSENT_PAGE).isEqualTo("/oauth/consent");
    }

    @Test
    void replacementMethodsRemoveEveryDefaultAndKeepOnlyTheSpecifiedComponentsInOrder() {
        AuthenticationConverter defaultConverter = request -> null;
        AuthenticationConverter firstConverter = request -> null;
        AuthenticationConverter secondConverter = request -> null;
        AuthenticationProvider defaultProvider = provider();
        AuthenticationProvider firstProvider = provider();
        AuthenticationProvider secondProvider = provider();

        List<AuthenticationConverter> clientConverters = new ArrayList<>(List.of(defaultConverter));
        CoreAgentAuthorizationServerConfigurerFactory.replaceClientAuthenticationConverters(clientConverters,
                List.of(firstConverter));
        assertThat(clientConverters).containsExactly(firstConverter);

        List<AuthenticationProvider> clientProviders = new ArrayList<>(List.of(defaultProvider));
        CoreAgentAuthorizationServerConfigurerFactory.replaceClientAuthenticationProviders(clientProviders,
                List.of(firstProvider));
        assertThat(clientProviders).containsExactly(firstProvider);

        List<AuthenticationConverter> authorizationConverters = new ArrayList<>(List.of(defaultConverter));
        CoreAgentAuthorizationServerConfigurerFactory.replaceAuthorizationEndpointConverters(authorizationConverters,
                List.of(firstConverter));
        assertThat(authorizationConverters).containsExactly(firstConverter);

        List<AuthenticationProvider> authorizationProviders = new ArrayList<>(List.of(defaultProvider));
        CoreAgentAuthorizationServerConfigurerFactory.replaceAuthorizationEndpointProviders(authorizationProviders,
                List.of(firstProvider, secondProvider));
        assertThat(authorizationProviders).containsExactly(firstProvider, secondProvider);

        List<AuthenticationConverter> tokenConverters = new ArrayList<>(List.of(defaultConverter));
        CoreAgentAuthorizationServerConfigurerFactory.replaceTokenEndpointConverters(tokenConverters,
                List.of(firstConverter, secondConverter));
        assertThat(tokenConverters).containsExactly(firstConverter, secondConverter);

        List<AuthenticationProvider> tokenProviders = new ArrayList<>(List.of(defaultProvider));
        CoreAgentAuthorizationServerConfigurerFactory.replaceTokenEndpointProviders(tokenProviders,
                List.of(firstProvider, secondProvider));
        assertThat(tokenProviders).containsExactly(firstProvider, secondProvider);

        assertThatThrownBy(() -> CoreAgentAuthorizationServerConfigurerFactory.replaceTokenEndpointConverters(
                new ArrayList<>(), List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void authorizationProviderChainPlacesProjectProvidersBeforeTheSasValidatorBootstrapProvider() {
        CoreAgentAuthorizationServerConfigurerFactory factory = factory();

        assertThat(factory.authorizationEndpointProviders()).hasSize(3);
        assertThat(factory.authorizationEndpointProviders().get(0))
                .isInstanceOf(CoreAgentAuthorizationCodeRequestAuthenticationProvider.class);
        assertThat(factory.authorizationEndpointProviders().get(1))
                .isInstanceOf(CoreAgentAuthorizationConsentAuthenticationProvider.class);
        assertThat(factory.authorizationEndpointProviders().get(2))
                .isInstanceOf(OAuth2AuthorizationCodeRequestAuthenticationProvider.class);
    }

    @Test
    void sasBootstrapProviderCannotPersistOrIssueACodeWhenReachedUnexpectedly() {
        ActiveRegisteredClientRepository clients = Mockito.mock(ActiveRegisteredClientRepository.class);
        RegisteredClient client = RegisteredClient.withId("bootstrap-client").clientId("bootstrap-client")
                .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://127.0.0.1:9090/bootstrap").scope("note:read")
                .clientSettings(ClientSettings.builder().requireProofKey(false).requireAuthorizationConsent(false).build())
                .build();
        when(clients.findByClientId("bootstrap-client")).thenReturn(client);
        CoreAgentAuthorizationServerConfigurerFactory factory = new CoreAgentAuthorizationServerConfigurerFactory(clients,
                new FailClosedOAuth2AuthorizationService(), mock(OAuth2AuthorizationConsentService.class),
                new CoreAgentAuthorizationServerConfiguration().coreAgentAuthorizationServerSettings(),
                Mockito.mock(CoreAgentPublicClientAuthenticationConverter.class), Mockito.mock(CoreAgentPublicClientAuthenticationProvider.class),
                Mockito.mock(CoreAgentAuthorizationEndpointAuthenticationConverter.class),
                mock(CoreAgentAuthorizationCodeRequestAuthenticationProvider.class),
                mock(CoreAgentAuthorizationConsentAuthenticationProvider.class),
                mock(CoreAgentAuthorizationCodeTokenAuthenticationConverter.class),
                mock(CoreAgentRefreshTokenAuthenticationConverter.class),
                mock(CoreAgentAuthorizationCodeTokenAuthenticationProvider.class),
                mock(CoreAgentRefreshTokenAuthenticationProvider.class));
        AuthenticationProvider bootstrap = factory.authorizationEndpointProviders().get(2);
        OAuth2AuthorizationCodeRequestAuthenticationToken request =
                new OAuth2AuthorizationCodeRequestAuthenticationToken("/oauth2/authorize", "bootstrap-client",
                        UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of()),
                        "http://127.0.0.1:9090/bootstrap", "state", Set.of("note:read"), Map.of());

        AuthorizationServerContext context = mock(AuthorizationServerContext.class);
        when(context.getIssuer()).thenReturn("http://127.0.0.1:9090");
        when(context.getAuthorizationServerSettings()).thenReturn(
                new CoreAgentAuthorizationServerConfiguration().coreAgentAuthorizationServerSettings());
        AuthorizationServerContextHolder.setContext(context);
        try {
            assertThatIllegalStateException().isThrownBy(() -> bootstrap.authenticate(request))
                    .withMessage(FailClosedOAuth2AuthorizationService.FAILURE_MESSAGE);
        } finally {
            AuthorizationServerContextHolder.resetContext();
        }
    }

    @Test
    void configurationProvidesOnlySettingsAndTheUnappliedFactoryWhenDependenciesExist() {
        runner.withUserConfiguration(DependenciesConfiguration.class)
                .run(context -> {
                    assertThat(context.getBeansOfType(AuthorizationServerSettings.class)).hasSize(1);
                    assertThat(context.getBeansOfType(CoreAgentAuthorizationServerConfigurerFactory.class)).hasSize(1);
                });
    }

    @Test
    void wiringDoesNotCreateSasAuthorizationPersistenceOrDefaultProtocolComponents() throws IOException {
        String configuration = Files.readString(Path.of("src/main/java/com/jacolp/module/system/biz/infrastructure/authorization/"
                + "CoreAgentAuthorizationServerConfiguration.java"));
        String factory = Files.readString(Path.of("src/main/java/com/jacolp/module/system/biz/infrastructure/authorization/"
                + "CoreAgentAuthorizationServerConfigurerFactory.java"));

        assertThat(configuration + factory).doesNotContain("InMemoryOAuth2AuthorizationService",
                "JdbcOAuth2AuthorizationService", "OAuth2TokenGenerator", ".apply(",
                "deviceAuthorizationEndpoint", "pushedAuthorizationRequestEndpoint", "oidc(");
        assertThat(factory).contains("authorizationService(authorizationService)",
                "replaceClientAuthenticationConverters", "replaceAuthorizationEndpointConverters",
                "replaceTokenEndpointConverters");
    }

    private static AuthenticationProvider provider() {
        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) {
                return null;
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return false;
            }
        };
    }

    private static CoreAgentAuthorizationServerConfigurerFactory factory() {
        return new CoreAgentAuthorizationServerConfigurerFactory(Mockito.mock(ActiveRegisteredClientRepository.class),
                Mockito.mock(FailClosedOAuth2AuthorizationService.class), mock(OAuth2AuthorizationConsentService.class),
                new CoreAgentAuthorizationServerConfiguration().coreAgentAuthorizationServerSettings(),
                mock(CoreAgentPublicClientAuthenticationConverter.class), mock(CoreAgentPublicClientAuthenticationProvider.class),
                mock(CoreAgentAuthorizationEndpointAuthenticationConverter.class),
                mock(CoreAgentAuthorizationCodeRequestAuthenticationProvider.class),
                mock(CoreAgentAuthorizationConsentAuthenticationProvider.class),
                mock(CoreAgentAuthorizationCodeTokenAuthenticationConverter.class),
                mock(CoreAgentRefreshTokenAuthenticationConverter.class),
                mock(CoreAgentAuthorizationCodeTokenAuthenticationProvider.class),
                mock(CoreAgentRefreshTokenAuthenticationProvider.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentAuthorizationServerConfiguration.class)
    static class ConfigurationUnderTest {
    }

    @Configuration(proxyBeanMethods = false)
    static class DependenciesConfiguration {
        @Bean ActiveRegisteredClientRepository registeredClientRepository() { return Mockito.mock(ActiveRegisteredClientRepository.class); }
        @Bean FailClosedOAuth2AuthorizationService authorizationService() { return Mockito.mock(FailClosedOAuth2AuthorizationService.class); }
        @Bean OAuth2AuthorizationConsentService authorizationConsentService() { return mock(OAuth2AuthorizationConsentService.class); }
        @Bean CoreAgentPublicClientAuthenticationConverter publicClientAuthenticationConverter() { return mock(CoreAgentPublicClientAuthenticationConverter.class); }
        @Bean CoreAgentPublicClientAuthenticationProvider publicClientAuthenticationProvider() { return mock(CoreAgentPublicClientAuthenticationProvider.class); }
        @Bean CoreAgentAuthorizationEndpointAuthenticationConverter authorizationEndpointAuthenticationConverter() { return mock(CoreAgentAuthorizationEndpointAuthenticationConverter.class); }
        @Bean CoreAgentAuthorizationCodeRequestAuthenticationProvider authorizationCodeRequestAuthenticationProvider() { return mock(CoreAgentAuthorizationCodeRequestAuthenticationProvider.class); }
        @Bean CoreAgentAuthorizationConsentAuthenticationProvider authorizationConsentAuthenticationProvider() { return mock(CoreAgentAuthorizationConsentAuthenticationProvider.class); }
        @Bean CoreAgentAuthorizationCodeTokenAuthenticationConverter authorizationCodeTokenAuthenticationConverter() { return mock(CoreAgentAuthorizationCodeTokenAuthenticationConverter.class); }
        @Bean CoreAgentRefreshTokenAuthenticationConverter refreshTokenAuthenticationConverter() { return mock(CoreAgentRefreshTokenAuthenticationConverter.class); }
        @Bean CoreAgentAuthorizationCodeTokenAuthenticationProvider authorizationCodeTokenAuthenticationProvider() { return mock(CoreAgentAuthorizationCodeTokenAuthenticationProvider.class); }
        @Bean CoreAgentRefreshTokenAuthenticationProvider refreshTokenAuthenticationProvider() { return mock(CoreAgentRefreshTokenAuthenticationProvider.class); }
    }
}
