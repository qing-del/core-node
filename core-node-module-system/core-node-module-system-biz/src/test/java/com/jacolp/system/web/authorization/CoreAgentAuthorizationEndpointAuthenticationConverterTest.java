package com.jacolp.system.web.authorization;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreAgentAuthorizationEndpointAuthenticationConverterTest {

    private static final String CHALLENGE = base64Url((byte) 5);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConverterOnlyConfiguration.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        AuthorizationServerContextHolder.resetContext();
    }

    @Test
    void getAuthorizationRequestUsesTheOfficialTokenAndTheDetailsSourceCreatesRedactedDetails() {
        installAuthorizationServerContext();
        authenticateBrowser();
        MockHttpServletRequest request = authorizationRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.99");
        CoreAgentAuthorizationEndpointAuthenticationConverter converter =
                new CoreAgentAuthorizationEndpointAuthenticationConverter();

        Object converted = converter.convert(request);

        assertThat(converted).isInstanceOf(OAuth2AuthorizationCodeRequestAuthenticationToken.class);
        OAuth2AuthorizationCodeRequestAuthenticationToken token =
                (OAuth2AuthorizationCodeRequestAuthenticationToken) converted;
        assertThat(token.getClientId()).isEqualTo("core_agent");
        assertThat(token.getDetails()).isNull();
        CoreAgentAuthorizationEndpointRequestDetails details =
                new CoreAgentAuthorizationEndpointAuthenticationDetailsSource().buildDetails(request);
        assertThat(details.session()).isSameAs(request.getSession(false));
        assertThat(details.sessionId()).isEqualTo(request.getSession(false).getId());
        assertThat(details.socketRemoteAddress()).isEqualTo("192.0.2.24");
        assertThat(details.originalScopeParameterPresent()).isFalse();
        assertThat(details.consentAction()).isNull();
        assertRedacted(details.toString(), details.sessionId(), "192.0.2.24");
    }

    @Test
    void postConsentUsesTheOfficialTokenAndDetailsSourceStrictlyParsesApproveOrDenyAction() {
        installAuthorizationServerContext();
        authenticateBrowser();
        CoreAgentAuthorizationEndpointAuthenticationConverter converter =
                new CoreAgentAuthorizationEndpointAuthenticationConverter();

        OAuth2AuthorizationConsentAuthenticationToken approve = consentToken(converter, "approve", false);
        OAuth2AuthorizationConsentAuthenticationToken deny = consentToken(converter, "deny", true);

        assertThat(approve.getScopes()).isEmpty();
        assertThat(approve.getDetails()).isNull();
        assertThat(deny.getDetails()).isNull();
        assertDetails(consentDetails("approve", false),
                CoreAgentAuthorizationEndpointRequestDetails.ConsentAction.APPROVE, false);
        assertDetails(consentDetails("deny", true),
                CoreAgentAuthorizationEndpointRequestDetails.ConsentAction.DENY, true);
    }

    @Test
    void detailsSourceRejectsMissingRepeatedOrInvalidConsentActionAndCreatesItsSession() {
        installAuthorizationServerContext();
        authenticateBrowser();
        CoreAgentAuthorizationEndpointAuthenticationDetailsSource detailsSource =
                new CoreAgentAuthorizationEndpointAuthenticationDetailsSource();

        for (String[] actions : new String[][]{null, {"approve", "deny"}, {""}, {"Approve"}, {"other"}}) {
            MockHttpServletRequest request = consentRequest(null, false);
            if (actions != null) {
                request.addParameter(CoreAgentAuthorizationEndpointAuthenticationConverter.CONSENT_ACTION_PARAMETER, actions);
            }
            assertInvalidRequest(() -> detailsSource.buildDetails(request),
                    CoreAgentAuthorizationEndpointAuthenticationConverter.INVALID_CONSENT_ACTION_DESCRIPTION);
        }

        MockHttpServletRequest noSession = consentRequest(null, false);
        noSession.addParameter(CoreAgentAuthorizationEndpointAuthenticationConverter.CONSENT_ACTION_PARAMETER, "approve");
        CoreAgentAuthorizationEndpointRequestDetails details = detailsSource.buildDetails(noSession);
        assertThat(details.session()).isSameAs(noSession.getSession(false));
    }

    @Test
    void unsupportedRequestReturnsNullAndSourceDoesNotReadForwardedHeaders() throws IOException {
        installAuthorizationServerContext();
        CoreAgentAuthorizationEndpointAuthenticationConverter converter =
                new CoreAgentAuthorizationEndpointAuthenticationConverter();
        MockHttpServletRequest unsupported = new MockHttpServletRequest("GET", "/not-an-authorization-endpoint");

        assertThat(converter.convert(unsupported)).isNull();
        String source = Files.readString(Path.of("src/main/java/com/jacolp/module/system/biz/web/authorization/"
                + "CoreAgentAuthorizationEndpointAuthenticationConverter.java"));
        assertThat(source).doesNotContain("getHeader", "X-Forwarded-For", "Forwarded", "getSessionAttribute");
    }

    @Test
    void registersConverter() {
        runner.run(context -> assertThat(context.getBeansOfType(
                CoreAgentAuthorizationEndpointAuthenticationConverter.class)).hasSize(1));
    }

    private static OAuth2AuthorizationConsentAuthenticationToken consentToken(
            CoreAgentAuthorizationEndpointAuthenticationConverter converter, String action, boolean includeScope) {
        MockHttpServletRequest request = consentRequest(new org.springframework.mock.web.MockHttpSession(), includeScope);
        request.addParameter(CoreAgentAuthorizationEndpointAuthenticationConverter.CONSENT_ACTION_PARAMETER, action);
        Object converted = converter.convert(request);
        assertThat(converted).isInstanceOf(OAuth2AuthorizationConsentAuthenticationToken.class);
        return (OAuth2AuthorizationConsentAuthenticationToken) converted;
    }

    private static MockHttpServletRequest consentRequest(HttpSession session, boolean includeScope) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/authorize");
        request.setRemoteAddr("192.0.2.24");
        request.setSession(session);
        request.addParameter("client_id", "core_agent");
        request.addParameter("state", "opaque-client-state");
        if (includeScope) {
            request.addParameter("scope", "note:read");
        }
        return request;
    }

    private static MockHttpServletRequest authorizationRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setRemoteAddr("192.0.2.24");
        request.setScheme("http");
        request.setServerName("127.0.0.1");
        request.setServerPort(8080);
        request.addParameter("response_type", "code");
        request.addParameter("client_id", "core_agent");
        request.addParameter("redirect_uri", "http://127.0.0.1:9090/oauth/callback");
        request.addParameter("state", "opaque-client-state");
        request.addParameter("code_challenge", CHALLENGE);
        request.addParameter("code_challenge_method", "S256");
        request.setQueryString("response_type=code&client_id=core_agent"
                + "&redirect_uri=http%3A%2F%2F127.0.0.1%3A9090%2Foauth%2Fcallback"
                + "&state=opaque-client-state&code_challenge=" + CHALLENGE + "&code_challenge_method=S256");
        return request;
    }

    private static void authenticateBrowser() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("42", null, java.util.List.of()));
    }

    private static void installAuthorizationServerContext() {
        AuthorizationServerSettings settings = AuthorizationServerSettings.builder().build();
        AuthorizationServerContextHolder.setContext(new AuthorizationServerContext() {
            @Override
            public String getIssuer() {
                return "http://127.0.0.1:8080";
            }

            @Override
            public AuthorizationServerSettings getAuthorizationServerSettings() {
                return settings;
            }
        });
    }

    private static CoreAgentAuthorizationEndpointRequestDetails consentDetails(String action, boolean includeScope) {
        MockHttpServletRequest request = consentRequest(new org.springframework.mock.web.MockHttpSession(), includeScope);
        request.addParameter(CoreAgentAuthorizationEndpointAuthenticationConverter.CONSENT_ACTION_PARAMETER, action);
        return new CoreAgentAuthorizationEndpointAuthenticationDetailsSource().buildDetails(request);
    }

    private static void assertDetails(CoreAgentAuthorizationEndpointRequestDetails details,
                                      CoreAgentAuthorizationEndpointRequestDetails.ConsentAction expectedAction,
                                      boolean scopePresent) {
        assertThat(details.consentAction()).isEqualTo(expectedAction);
        assertThat(details.originalScopeParameterPresent()).isEqualTo(scopePresent);
    }

    private static void assertInvalidRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                                             String description) {
        assertThatThrownBy(call).isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(exception -> {
                    OAuth2AuthenticationException oauth = (OAuth2AuthenticationException) exception;
                    assertThat(oauth.getError().getErrorCode()).isEqualTo("invalid_request");
                    assertThat(oauth.getError().getDescription()).isEqualTo(description);
                });
    }

    private static String base64Url(byte fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, fill);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentAuthorizationEndpointAuthenticationConverter.class)
    static class ConverterOnlyConfiguration {
    }

    private static void assertRedacted(String value, String... values) {
        assertThat(value).contains("<redacted>").doesNotContain(values);
    }
}
