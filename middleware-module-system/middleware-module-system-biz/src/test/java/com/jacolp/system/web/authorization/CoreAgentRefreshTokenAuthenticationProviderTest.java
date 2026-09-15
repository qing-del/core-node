package com.jacolp.system.web.authorization;

import com.jacolp.system.application.authorization.CoreAgentRefreshTokenRejectedException;
import com.jacolp.system.application.authorization.CoreAgentRefreshTokenService;
import com.jacolp.system.application.authorization.CoreAgentRegisteredClientPolicyResolver;
import com.jacolp.system.application.authorization.model.CoreAgentRefreshTokenRequest;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentRefreshTokens;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreAgentRefreshTokenAuthenticationProviderTest {

    private static final Instant NOW = Instant.parse("2026-08-11T14:00:00Z");
    private static final String RAW_REFRESH = "A".repeat(43);
    private static final String REDIRECT_URI = "http://127.0.0.1:9090/oauth/callback";
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProviderOnlyConfiguration.class);

    @Test
    void mapsMissingAndExplicitScopeRequestsToServiceThenReturnsOfficialRotatedTokenResponse() {
        Fixture missing = fixture();
        when(missing.service.refresh(any())).thenReturn(issued());
        Authentication result = missing.provider.authenticate(request(missing.client, Set.of(), false));

        assertSuccess(result, missing);
        ArgumentCaptor<CoreAgentRefreshTokenRequest> missingRequest = ArgumentCaptor.forClass(CoreAgentRefreshTokenRequest.class);
        verify(missing.service).refresh(missingRequest.capture());
        assertThat(missingRequest.getValue().requestedScopes()).isNull();
        assertThat(missingRequest.getValue().rawRefreshToken()).isEqualTo(RAW_REFRESH);

        Fixture explicit = fixture();
        when(explicit.service.refresh(any())).thenReturn(issued());
        explicit.provider.authenticate(request(explicit.client, Set.of("note:read"), true));
        ArgumentCaptor<CoreAgentRefreshTokenRequest> explicitRequest = ArgumentCaptor.forClass(CoreAgentRefreshTokenRequest.class);
        verify(explicit.service).refresh(explicitRequest.capture());
        assertThat(explicitRequest.getValue().requestedScopes()).containsExactly("note:read");
    }

    @Test
    void clientAndDetailsGatesFailClosedWithStandardErrors() {
        Fixture secretMethod = fixture();
        OAuth2ClientAuthenticationToken invalidClient = new OAuth2ClientAuthenticationToken(secretMethod.registeredClient,
                ClientAuthenticationMethod.CLIENT_SECRET_BASIC, null);
        assertError(() -> secretMethod.provider.authenticate(request(invalidClient, Set.of(), false)), "invalid_client");
        Mockito.verifyNoInteractions(secretMethod.service);

        Fixture wrongClient = fixture();
        OAuth2ClientAuthenticationToken other = new OAuth2ClientAuthenticationToken(registeredClient("other", "other"),
                ClientAuthenticationMethod.NONE, null);
        assertError(() -> wrongClient.provider.authenticate(request(other, Set.of(), false)), "unauthorized_client");

        Fixture noDetails = fixture();
        OAuth2RefreshTokenAuthenticationToken request = new OAuth2RefreshTokenAuthenticationToken(RAW_REFRESH,
                noDetails.client, Set.of(), Map.of());
        assertError(() -> noDetails.provider.authenticate(request), "invalid_grant");
        Mockito.verifyNoInteractions(noDetails.service);
    }

    @Test
    void ordinaryRefreshRejectionMapsToInvalidGrantButSystemFailuresPropagate() {
        Fixture rejected = fixture();
        when(rejected.service.refresh(any())).thenThrow(new CoreAgentRefreshTokenRejectedException());
        assertError(() -> rejected.provider.authenticate(request(rejected.client, Set.of(), false)), "invalid_grant");

        Fixture failure = fixture();
        IllegalStateException redisFailure = new IllegalStateException("redis unavailable");
        when(failure.service.refresh(any())).thenThrow(redisFailure);
        assertThatThrownBy(() -> failure.provider.authenticate(request(failure.client, Set.of(), false)))
                .isSameAs(redisFailure);
    }

    @Test
    void supportsOnlyOfficialRefreshTokenAndHasNoTokenPersistenceOrSensitiveLogging() throws IOException {
        Fixture fixture = fixture();
        assertThat(fixture.provider.supports(OAuth2RefreshTokenAuthenticationToken.class)).isTrue();
        assertThat(fixture.provider.supports(OAuth2ClientAuthenticationToken.class)).isFalse();
        String source = Files.readString(Path.of("src/main/java/com/jacolp/system/web/authorization/"
                + "CoreAgentRefreshTokenAuthenticationProvider.java"));
        assertThat(source).doesNotContain("OAuth2AuthorizationService", "OAuth2TokenGenerator", "Logger", "rawRefreshToken=");
        runner.withUserConfiguration(DependencyConfiguration.class)
                .run(context -> assertThat(context.getBeansOfType(CoreAgentRefreshTokenAuthenticationProvider.class)).hasSize(1));
    }

    private static void assertSuccess(Authentication result, Fixture fixture) {
        assertThat(result).isInstanceOf(OAuth2AccessTokenAuthenticationToken.class);
        OAuth2AccessTokenAuthenticationToken success = (OAuth2AccessTokenAuthenticationToken) result;
        assertThat(success.getRegisteredClient()).isSameAs(fixture.registeredClient);
        assertThat(success.getPrincipal()).isSameAs(fixture.client);
        assertThat(success.getAccessToken().getTokenValue()).isEqualTo("access-token");
        assertThat(success.getAccessToken().getIssuedAt()).isEqualTo(NOW);
        assertThat(success.getAccessToken().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(success.getAccessToken().getScopes()).containsExactly("note:read");
        assertThat(success.getRefreshToken().getTokenValue()).isEqualTo("next-refresh-token");
        assertThat(success.getRefreshToken().getIssuedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(success.getRefreshToken().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(success.getAdditionalParameters()).isEmpty();
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String expectedCode) {
        assertThatThrownBy(call).isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(exception -> assertThat(((OAuth2AuthenticationException) exception).getError().getErrorCode())
                        .isEqualTo(expectedCode));
    }

    private static Fixture fixture() {
        CoreAgentRegisteredClientPolicyResolver policyResolver = mock(CoreAgentRegisteredClientPolicyResolver.class);
        CoreAgentRefreshTokenService service = mock(CoreAgentRefreshTokenService.class);
        CoreAgentRegisteredClientPolicy policy = new CoreAgentRegisteredClientPolicy("registered-core-agent", "core_agent",
                REDIRECT_URI, Set.of("note:read"), Set.of("note:read"), "0.0.0.0/0", Duration.ofHours(1),
                Duration.ofHours(24), Duration.ofMinutes(10));
        RegisteredClient registeredClient = registeredClient("registered-core-agent", "core_agent");
        OAuth2ClientAuthenticationToken client = new OAuth2ClientAuthenticationToken(registeredClient,
                ClientAuthenticationMethod.NONE, null);
        when(policyResolver.resolve("core_agent")).thenReturn(policy);
        return new Fixture(new CoreAgentRefreshTokenAuthenticationProvider(policyResolver, service), policyResolver,
                service, registeredClient, client);
    }

    private static RegisteredClient registeredClient(String id, String clientId) {
        return RegisteredClient.withId(id).clientId(clientId).clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN).redirectUri(REDIRECT_URI).scope("note:read")
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true).build()).build();
    }

    private static OAuth2RefreshTokenAuthenticationToken request(OAuth2ClientAuthenticationToken client, Set<String> scopes,
                                                                  boolean scopePresent) {
        OAuth2RefreshTokenAuthenticationToken request = new OAuth2RefreshTokenAuthenticationToken(RAW_REFRESH, client,
                scopes, Map.of());
        request.setDetails(new CoreAgentRefreshTokenRequestDetails("192.0.2.24", scopePresent));
        return request;
    }

    private static IssuedCoreAgentRefreshTokens issued() {
        return new IssuedCoreAgentRefreshTokens("access-token", "next-refresh-token", "Bearer", NOW,
                NOW.plus(Duration.ofHours(1)), NOW.plusSeconds(1), NOW.plus(Duration.ofHours(24)), List.of("note:read"));
    }

    private record Fixture(CoreAgentRefreshTokenAuthenticationProvider provider,
                           CoreAgentRegisteredClientPolicyResolver policyResolver,
                           CoreAgentRefreshTokenService service,
                           RegisteredClient registeredClient,
                           OAuth2ClientAuthenticationToken client) {
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentRefreshTokenAuthenticationProvider.class)
    static class ProviderOnlyConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class DependencyConfiguration {
        @Bean CoreAgentRegisteredClientPolicyResolver policyResolver() { return mock(CoreAgentRegisteredClientPolicyResolver.class); }
        @Bean CoreAgentRefreshTokenService refreshTokenService() { return mock(CoreAgentRefreshTokenService.class); }
    }
}
