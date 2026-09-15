package com.jacolp.system.web.authorization;

import com.jacolp.system.application.authorization.CoreAgentRegisteredClientPolicyResolver;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.infrastructure.authorization.ActiveRegisteredClientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CoreAgentPublicClientAuthenticationTest {

    private static final String REDIRECT_URI = "http://127.0.0.1:9090/oauth/callback";
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ComponentsOnlyConfiguration.class);

    @Test
    void convertsBothFixedTokenGrantsToAnUnauthenticatedPublicClient() {
        CoreAgentPublicClientAuthenticationConverter converter = new CoreAgentPublicClientAuthenticationConverter();

        OAuth2ClientAuthenticationToken code = convert(converter, tokenRequest("authorization_code"));
        OAuth2ClientAuthenticationToken refresh = convert(converter, tokenRequest("refresh_token"));

        assertPublicClientRequest(code, "authorization_code");
        assertPublicClientRequest(refresh, "refresh_token");
    }

    @Test
    void converterRejectsClientOrCredentialPollutionAndIgnoresOtherTokenShapes() throws IOException {
        CoreAgentPublicClientAuthenticationConverter converter = new CoreAgentPublicClientAuthenticationConverter();

        MockHttpServletRequest missingClient = tokenRequest("authorization_code");
        missingClient.removeParameter("client_id");
        assertInvalidClient(() -> converter.convert(missingClient));

        MockHttpServletRequest duplicateClient = tokenRequest("authorization_code");
        duplicateClient.addParameter("client_id", "core_agent");
        assertInvalidClient(() -> converter.convert(duplicateClient));

        MockHttpServletRequest otherClient = tokenRequest("authorization_code");
        otherClient.setParameter("client_id", "another-client");
        assertInvalidClient(() -> converter.convert(otherClient));

        MockHttpServletRequest basic = tokenRequest("refresh_token");
        basic.addHeader(HttpHeaders.AUTHORIZATION, "Basic redacted");
        assertInvalidClient(() -> converter.convert(basic));

        for (String parameter : Set.of("client_secret", "client_assertion", "client_assertion_type")) {
            MockHttpServletRequest polluted = tokenRequest("refresh_token");
            polluted.addParameter(parameter, "redacted");
            assertInvalidClient(() -> converter.convert(polluted));
        }

        MockHttpServletRequest unsupportedGrant = tokenRequest("custom");
        assertThat(converter.convert(unsupportedGrant)).isNull();
        MockHttpServletRequest duplicateGrant = tokenRequest("authorization_code");
        duplicateGrant.addParameter("grant_type", "refresh_token");
        assertThat(converter.convert(duplicateGrant)).isNull();
        MockHttpServletRequest wrongPath = tokenRequest("authorization_code");
        wrongPath.setRequestURI("/oauth2/token");
        assertThat(converter.convert(wrongPath)).isNull();
        MockHttpServletRequest wrongMethod = tokenRequest("authorization_code");
        wrongMethod.setMethod("GET");
        assertThat(converter.convert(wrongMethod)).isNull();

        String source = Files.readString(Path.of("src/main/java/com/jacolp/module/system/biz/web/authorization/"
                + "CoreAgentPublicClientAuthenticationConverter.java"));
        assertThat(source).doesNotContain("X-Forwarded-For", "Forwarded", "getRemoteAddr", "Logger",
                "getParameter(\"client_secret\")");
    }

    @Test
    void providerAuthenticatesOnlyTheFixedActivePublicClientForBothGrants() {
        Fixture fixture = fixture();
        for (String grantType : Set.of("authorization_code", "refresh_token")) {
            Authentication success = fixture.provider.authenticate(publicClientRequest("core_agent",
                    ClientAuthenticationMethod.NONE, grantType));
            assertThat(success).isInstanceOf(OAuth2ClientAuthenticationToken.class);
            OAuth2ClientAuthenticationToken client = (OAuth2ClientAuthenticationToken) success;
            assertThat(client.isAuthenticated()).isTrue();
            assertThat(client.getRegisteredClient()).isSameAs(fixture.registeredClient);
            assertThat(client.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.NONE);
            assertThat(client.getCredentials()).isNull();
        }
        verify(fixture.repository, times(2)).findByClientId("core_agent");
    }

    @Test
    void providerFailsClosedForWrongIdentityDisabledOrMismatchedClientAndPropagatesSystemFailures() {
        Fixture wrongMethod = fixture();
        assertInvalidClient(() -> wrongMethod.provider.authenticate(publicClientRequest("core_agent",
                ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "authorization_code")));
        verifyNoInteractions(wrongMethod.policyResolver, wrongMethod.repository);

        Fixture wrongClient = fixture();
        assertInvalidClient(() -> wrongClient.provider.authenticate(publicClientRequest("other",
                ClientAuthenticationMethod.NONE, "authorization_code")));

        Fixture missingDetails = fixture();
        OAuth2ClientAuthenticationToken noDetails = new OAuth2ClientAuthenticationToken("core_agent",
                ClientAuthenticationMethod.NONE, null, Map.of());
        assertInvalidClient(() -> missingDetails.provider.authenticate(noDetails));

        Fixture disabled = fixture();
        when(disabled.repository.findByClientId("core_agent")).thenReturn(null);
        assertOAuthError(() -> disabled.provider.authenticate(publicClientRequest("core_agent",
                ClientAuthenticationMethod.NONE, "refresh_token")), "unauthorized_client");

        Fixture mismatch = fixture();
        when(mismatch.repository.findByClientId("core_agent")).thenReturn(registeredClient("another-id", "core_agent"));
        assertOAuthError(() -> mismatch.provider.authenticate(publicClientRequest("core_agent",
                ClientAuthenticationMethod.NONE, "refresh_token")), "unauthorized_client");

        Fixture policyFailure = fixture();
        IllegalStateException metadataFailure = new IllegalStateException("metadata unavailable");
        when(policyFailure.policyResolver.resolve("core_agent")).thenThrow(metadataFailure);
        assertThatThrownBy(() -> policyFailure.provider.authenticate(publicClientRequest("core_agent",
                ClientAuthenticationMethod.NONE, "authorization_code"))).isSameAs(metadataFailure);
    }

    @Test
    void componentsAreAlwaysRegisteredAndContainNoSasAuthorizationPersistenceOrSecretLogging() throws IOException {
        Fixture fixture = fixture();
        assertThat(fixture.provider.supports(OAuth2ClientAuthenticationToken.class)).isTrue();
        assertThat(fixture.provider.supports(UsernamePasswordAuthenticationToken.class)).isFalse();
        runner.withUserConfiguration(DependencyConfiguration.class)
                .run(context -> {
                    assertThat(context.getBeansOfType(CoreAgentPublicClientAuthenticationConverter.class)).hasSize(1);
                    assertThat(context.getBeansOfType(CoreAgentPublicClientAuthenticationProvider.class)).hasSize(1);
                });

        String providerSource = Files.readString(Path.of("src/main/java/com/jacolp/module/system/biz/web/authorization/"
                + "CoreAgentPublicClientAuthenticationProvider.java"));
        assertThat(providerSource).doesNotContain("OAuth2AuthorizationService", "CodeVerifier", "Logger",
                "getClientSecret() +", "client_secret=");
        assertThat(new CoreAgentPublicClientAuthenticationDetails("authorization_code").toString())
                .contains("redacted").doesNotContain("authorization_code");
    }

    private static OAuth2ClientAuthenticationToken convert(CoreAgentPublicClientAuthenticationConverter converter,
                                                            MockHttpServletRequest request) {
        Authentication authentication = converter.convert(request);
        assertThat(authentication).isInstanceOf(OAuth2ClientAuthenticationToken.class);
        return (OAuth2ClientAuthenticationToken) authentication;
    }

    private static void assertPublicClientRequest(OAuth2ClientAuthenticationToken authentication, String grantType) {
        assertThat(authentication.isAuthenticated()).isFalse();
        assertThat(authentication.getPrincipal()).isEqualTo("core_agent");
        assertThat(authentication.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.NONE);
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getDetails()).isNull();
        assertThat(authentication.getAdditionalParameters()).isEqualTo(Map.of("core_agent_grant_type", grantType));
    }

    private static MockHttpServletRequest tokenRequest(String grantType) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth/token");
        request.addParameter("grant_type", grantType);
        request.addParameter("client_id", "core_agent");
        return request;
    }

    private static OAuth2ClientAuthenticationToken publicClientRequest(String clientId,
                                                                        ClientAuthenticationMethod method,
                                                                        String grantType) {
        return new OAuth2ClientAuthenticationToken(clientId, method, null,
                Map.of("core_agent_grant_type", grantType));
    }

    private static Fixture fixture() {
        CoreAgentRegisteredClientPolicyResolver resolver = mock(CoreAgentRegisteredClientPolicyResolver.class);
        ActiveRegisteredClientRepository repository = mock(ActiveRegisteredClientRepository.class);
        CoreAgentRegisteredClientPolicy policy = new CoreAgentRegisteredClientPolicy("registered-core-agent", "core_agent",
                REDIRECT_URI, Set.of("note:read"), Set.of("note:read"), "0.0.0.0/0", Duration.ofHours(1),
                Duration.ofHours(24), Duration.ofMinutes(10));
        RegisteredClient registeredClient = registeredClient("registered-core-agent", "core_agent");
        when(resolver.resolve("core_agent")).thenReturn(policy);
        when(repository.findByClientId("core_agent")).thenReturn(registeredClient);
        return new Fixture(new CoreAgentPublicClientAuthenticationProvider(resolver, repository), resolver, repository,
                registeredClient);
    }

    private static RegisteredClient registeredClient(String id, String clientId) {
        return RegisteredClient.withId(id).clientId(clientId).clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN).redirectUri(REDIRECT_URI).scope("note:read")
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true).build())
                .build();
    }

    private static void assertInvalidClient(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertOAuthError(call, "invalid_client");
    }

    private static void assertOAuthError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(exception -> assertThat(((OAuth2AuthenticationException) exception).getError().getErrorCode())
                        .isEqualTo(code));
    }

    private record Fixture(CoreAgentPublicClientAuthenticationProvider provider,
                           CoreAgentRegisteredClientPolicyResolver policyResolver,
                           ActiveRegisteredClientRepository repository,
                           RegisteredClient registeredClient) {
    }

    @Configuration(proxyBeanMethods = false)
    @Import({CoreAgentPublicClientAuthenticationConverter.class, CoreAgentPublicClientAuthenticationProvider.class})
    static class ComponentsOnlyConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class DependencyConfiguration {
        @Bean CoreAgentRegisteredClientPolicyResolver policyResolver() { return mock(CoreAgentRegisteredClientPolicyResolver.class); }
        @Bean ActiveRegisteredClientRepository registeredClientRepository() { return mock(ActiveRegisteredClientRepository.class); }
    }
}
