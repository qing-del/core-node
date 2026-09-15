package com.jacolp.system.web.authorization;

import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationCodeExchangeRequest;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationCodeTokens;
import com.jacolp.system.application.authorization.model.VerifiedCoreAgentAuthorizationCode;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationCodeExchangeRejectedException;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationCodeExchangeService;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationCodeTokenRejectedException;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationCodeTokenService;
import com.jacolp.system.application.authorization.CoreAgentRegisteredClientPolicyResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CoreAgentAuthorizationCodeTokenAuthenticationProviderTest {

    private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");
    private static final String RAW_CODE = "A".repeat(43);
    private static final String VERIFIER = "B".repeat(43);
    private static final String REDIRECT_URI = "http://127.0.0.1:9090/oauth/callback";
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProviderOnlyConfiguration.class);

    @Test
    void exchangesVerifiedCodeThenReturnsOfficialTokenResponseWithActualTimesAndScopes() {
        Fixture fixture = fixture();
        OAuth2AuthorizationCodeAuthenticationToken request = request(fixture.client, "192.0.2.24");
        when(fixture.exchange.exchange(any())).thenReturn(verified());
        when(fixture.tokenService.issue(verified())).thenReturn(issued(false));

        Authentication result = fixture.provider.authenticate(request);

        assertThat(result).isInstanceOf(OAuth2AccessTokenAuthenticationToken.class);
        OAuth2AccessTokenAuthenticationToken success = (OAuth2AccessTokenAuthenticationToken) result;
        assertThat(success.getRegisteredClient()).isSameAs(fixture.registeredClient);
        assertThat(success.getPrincipal()).isSameAs(fixture.client);
        assertThat(success.getAccessToken().getTokenValue()).isEqualTo("access-token");
        assertThat(success.getAccessToken().getIssuedAt()).isEqualTo(NOW);
        assertThat(success.getAccessToken().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(success.getAccessToken().getScopes()).containsExactly("note:read");
        assertThat(success.getRefreshToken().getTokenValue()).isEqualTo("refresh-token");
        assertThat(success.getRefreshToken().getIssuedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(success.getRefreshToken().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(success.getAdditionalParameters()).isEmpty();

        ArgumentCaptor<CoreAgentAuthorizationCodeExchangeRequest> exchangeRequest =
                ArgumentCaptor.forClass(CoreAgentAuthorizationCodeExchangeRequest.class);
        verify(fixture.exchange).exchange(exchangeRequest.capture());
        assertThat(exchangeRequest.getValue().rawCode()).isEqualTo(RAW_CODE);
        assertThat(exchangeRequest.getValue().clientId()).isEqualTo("core_agent");
        assertThat(exchangeRequest.getValue().redirectUri()).isEqualTo(REDIRECT_URI);
        assertThat(exchangeRequest.getValue().codeVerifier()).isEqualTo(VERIFIER);
        assertThat(exchangeRequest.getValue().socketRemoteAddress()).isEqualTo("192.0.2.24");
        verify(fixture.tokenService).issue(verified());
    }

    @Test
    void clientAuthenticationAndRegisteredClientGatesFailClosedWithOAuthErrors() {
        Fixture fixture = fixture();
        OAuth2ClientAuthenticationToken secretMethod = new OAuth2ClientAuthenticationToken(fixture.registeredClient,
                ClientAuthenticationMethod.CLIENT_SECRET_BASIC, null);
        assertError(() -> fixture.provider.authenticate(request(secretMethod, "192.0.2.24")), "invalid_client");
        verifyNoInteractions(fixture.exchange, fixture.tokenService);

        Fixture wrongClient = fixture();
        RegisteredClient other = registeredClient("other", "other-client");
        OAuth2ClientAuthenticationToken otherPrincipal = new OAuth2ClientAuthenticationToken(other,
                ClientAuthenticationMethod.NONE, null);
        assertError(() -> wrongClient.provider.authenticate(request(otherPrincipal, "192.0.2.24")), "unauthorized_client");
        verifyNoInteractions(wrongClient.exchange, wrongClient.tokenService);

        Fixture malformedRegisteredClient = fixture();
        RegisteredClient noPkce = RegisteredClient.withId("registered-core-agent").clientId("core_agent")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(REDIRECT_URI).scope("note:read")
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).requireProofKey(false).build())
                .build();
        assertError(() -> malformedRegisteredClient.provider.authenticate(request(
                new OAuth2ClientAuthenticationToken(noPkce, ClientAuthenticationMethod.NONE, null), "192.0.2.24")),
                "unauthorized_client");
    }

    @Test
    void detailsAndUniformCodeFailuresMapToInvalidGrantWithoutCallingTheNextService() {
        Fixture missingDetails = fixture();
        OAuth2AuthorizationCodeAuthenticationToken noDetails = new OAuth2AuthorizationCodeAuthenticationToken(RAW_CODE,
                missingDetails.client, REDIRECT_URI, Map.of("code_verifier", VERIFIER));
        assertError(() -> missingDetails.provider.authenticate(noDetails), "invalid_grant");
        verifyNoInteractions(missingDetails.exchange, missingDetails.tokenService);

        Fixture exchangeRejected = fixture();
        when(exchangeRejected.exchange.exchange(any())).thenThrow(new CoreAgentAuthorizationCodeExchangeRejectedException());
        assertError(() -> exchangeRejected.provider.authenticate(request(exchangeRejected.client, "192.0.2.24")),
                "invalid_grant");
        verify(exchangeRejected.tokenService, never()).issue(any());

        Fixture tokenRejected = fixture();
        when(tokenRejected.exchange.exchange(any())).thenReturn(verified());
        when(tokenRejected.tokenService.issue(verified())).thenThrow(new CoreAgentAuthorizationCodeTokenRejectedException());
        assertError(() -> tokenRejected.provider.authenticate(request(tokenRejected.client, "192.0.2.24")),
                "invalid_grant");
    }

    @Test
    void systemFailuresPropagateAndSocketWarningHasNoSensitiveValuesInSource() throws IOException {
        Fixture systemFailure = fixture();
        IllegalStateException unavailable = new IllegalStateException("redis unavailable");
        when(systemFailure.exchange.exchange(any())).thenThrow(unavailable);
        assertThatThrownBy(() -> systemFailure.provider.authenticate(request(systemFailure.client, "192.0.2.24")))
                .isSameAs(unavailable);

        Fixture socketChanged = fixture();
        when(socketChanged.exchange.exchange(any())).thenReturn(verified());
        when(socketChanged.tokenService.issue(verified())).thenReturn(issued(true));
        assertThat(socketChanged.provider.authenticate(request(socketChanged.client, "192.0.2.24")))
                .isInstanceOf(OAuth2AccessTokenAuthenticationToken.class);

        String source = Files.readString(Path.of("src/main/java/com/jacolp/module/system/biz/web/authorization/"
                + "CoreAgentAuthorizationCodeTokenAuthenticationProvider.java"));
        assertThat(source).doesNotContain("OAuth2AuthorizationService", "OAuth2TokenGenerator", "code_verifier=",
                "rawCode=", "socketRemoteAddress=");
        assertThat(source).contains("LOGGER.warn(\"CORE AGENT authorization-code token issued with changed socket address for client core_agent\")");
    }

    @Test
    void supportsOnlyOfficialAuthorizationCodeTokenAndIsAlwaysRegistered() {
        Fixture fixture = fixture();
        assertThat(fixture.provider.supports(OAuth2AuthorizationCodeAuthenticationToken.class)).isTrue();
        assertThat(fixture.provider.supports(OAuth2ClientAuthenticationToken.class)).isFalse();
        runner.withUserConfiguration(DependencyConfiguration.class)
                .run(context -> assertThat(context.getBeansOfType(
                        CoreAgentAuthorizationCodeTokenAuthenticationProvider.class)).hasSize(1));
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String expectedCode) {
        assertThatThrownBy(call).isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(exception -> assertThat(((OAuth2AuthenticationException) exception).getError().getErrorCode())
                        .isEqualTo(expectedCode));
    }

    private static Fixture fixture() {
        CoreAgentRegisteredClientPolicyResolver policyResolver = mock(CoreAgentRegisteredClientPolicyResolver.class);
        CoreAgentAuthorizationCodeExchangeService exchange = mock(CoreAgentAuthorizationCodeExchangeService.class);
        CoreAgentAuthorizationCodeTokenService tokenService = mock(CoreAgentAuthorizationCodeTokenService.class);
        CoreAgentRegisteredClientPolicy policy = new CoreAgentRegisteredClientPolicy("registered-core-agent", "core_agent",
                REDIRECT_URI, Set.of("note:read"), Set.of("note:read"), "0.0.0.0/0", Duration.ofHours(1),
                Duration.ofHours(24), Duration.ofMinutes(10));
        RegisteredClient registeredClient = registeredClient("registered-core-agent", "core_agent");
        OAuth2ClientAuthenticationToken client = new OAuth2ClientAuthenticationToken(registeredClient,
                ClientAuthenticationMethod.NONE, null);
        when(policyResolver.resolve("core_agent")).thenReturn(policy);
        return new Fixture(new CoreAgentAuthorizationCodeTokenAuthenticationProvider(policyResolver, exchange, tokenService),
                policyResolver, exchange, tokenService, registeredClient, client);
    }

    private static RegisteredClient registeredClient(String id, String clientId) {
        return RegisteredClient.withId(id).clientId(clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(REDIRECT_URI).scope("note:read")
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true).build())
                .build();
    }

    private static OAuth2AuthorizationCodeAuthenticationToken request(OAuth2ClientAuthenticationToken client,
                                                                        String socketRemoteAddress) {
        OAuth2AuthorizationCodeAuthenticationToken request = new OAuth2AuthorizationCodeAuthenticationToken(RAW_CODE,
                client, REDIRECT_URI, Map.of("code_verifier", VERIFIER));
        request.setDetails(new CoreAgentAuthorizationCodeTokenRequestDetails(socketRemoteAddress));
        return request;
    }

    private static VerifiedCoreAgentAuthorizationCode verified() {
        return new VerifiedCoreAgentAuthorizationCode("registered-core-agent", "core_agent", 7L, "alice", 3L,
                List.of("note:read"), AccountGrantTypeResolver.AUTHORIZATION_CODE, false);
    }

    private static IssuedCoreAgentAuthorizationCodeTokens issued(boolean socketAddressChanged) {
        return new IssuedCoreAgentAuthorizationCodeTokens("access-token", "refresh-token", "Bearer", NOW,
                NOW.plus(Duration.ofHours(1)), NOW.plusSeconds(1), NOW.plus(Duration.ofHours(24)), List.of("note:read"),
                socketAddressChanged);
    }

    private record Fixture(CoreAgentAuthorizationCodeTokenAuthenticationProvider provider,
                           CoreAgentRegisteredClientPolicyResolver policyResolver,
                           CoreAgentAuthorizationCodeExchangeService exchange,
                           CoreAgentAuthorizationCodeTokenService tokenService,
                           RegisteredClient registeredClient,
                           OAuth2ClientAuthenticationToken client) {
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentAuthorizationCodeTokenAuthenticationProvider.class)
    static class ProviderOnlyConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class DependencyConfiguration {
        @Bean CoreAgentRegisteredClientPolicyResolver policyResolver() { return mock(CoreAgentRegisteredClientPolicyResolver.class); }
        @Bean CoreAgentAuthorizationCodeExchangeService exchangeService() { return mock(CoreAgentAuthorizationCodeExchangeService.class); }
        @Bean CoreAgentAuthorizationCodeTokenService tokenService() { return mock(CoreAgentAuthorizationCodeTokenService.class); }
    }
}
