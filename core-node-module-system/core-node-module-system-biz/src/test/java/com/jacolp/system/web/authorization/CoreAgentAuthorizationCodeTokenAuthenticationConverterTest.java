package com.jacolp.system.web.authorization;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreAgentAuthorizationCodeTokenAuthenticationConverterTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConverterOnlyConfiguration.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void delegatesValidAuthorizationCodeParametersAndLeavesSocketDetailsToTheEndpointFilter() {
        UsernamePasswordAuthenticationToken client = UsernamePasswordAuthenticationToken.authenticated("core_agent", null,
                java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(client);
        MockHttpServletRequest request = tokenRequest();
        request.setRemoteAddr("192.0.2.24");
        request.addHeader("X-Forwarded-For", "203.0.113.99");

        Object converted = new CoreAgentAuthorizationCodeTokenAuthenticationConverter().convert(request);

        assertThat(converted).isInstanceOf(OAuth2AuthorizationCodeAuthenticationToken.class);
        OAuth2AuthorizationCodeAuthenticationToken token = (OAuth2AuthorizationCodeAuthenticationToken) converted;
        assertThat(token.getGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(token.getCode()).isEqualTo("opaque-authorization-code");
        assertThat(token.getRedirectUri()).isEqualTo("http://127.0.0.1:9090/oauth/callback");
        assertThat(token.getPrincipal()).isSameAs(client);
        assertThat(token.getAdditionalParameters()).containsEntry("code_verifier", "A".repeat(43));
        assertThat(token.getAdditionalParameters()).doesNotContainKey("code");
        assertThat(token.getDetails()).isNull();
        CoreAgentAuthorizationCodeTokenRequestDetails details =
                (CoreAgentAuthorizationCodeTokenRequestDetails) new CoreAgentTokenEndpointAuthenticationDetailsSource()
                        .buildDetails(request);
        assertThat(details.socketRemoteAddress()).isEqualTo("192.0.2.24");
        assertThat(details.toString()).contains("<redacted>").doesNotContain("192.0.2.24");
    }

    @Test
    void officialConverterRetainsInvalidRequestBoundaries() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("core_agent", null, java.util.List.of()));
        CoreAgentAuthorizationCodeTokenAuthenticationConverter converter =
                new CoreAgentAuthorizationCodeTokenAuthenticationConverter();

        MockHttpServletRequest missingCode = tokenRequest();
        missingCode.removeParameter("code");
        assertInvalidRequest(() -> converter.convert(missingCode));

        MockHttpServletRequest repeatedCode = tokenRequest();
        repeatedCode.addParameter("code", "second-code");
        assertInvalidRequest(() -> converter.convert(repeatedCode));

        MockHttpServletRequest blankCode = tokenRequest();
        blankCode.setParameter("code", " ");
        assertInvalidRequest(() -> converter.convert(blankCode));

        MockHttpServletRequest repeatedRedirect = tokenRequest();
        repeatedRedirect.addParameter("redirect_uri", "http://127.0.0.1:9090/other");
        assertInvalidRequest(() -> converter.convert(repeatedRedirect));
    }

    @Test
    void unsupportedPathMethodOrGrantReturnsNullBeforeDelegating() throws IOException {
        CoreAgentAuthorizationCodeTokenAuthenticationConverter converter =
                new CoreAgentAuthorizationCodeTokenAuthenticationConverter();
        MockHttpServletRequest wrongPath = tokenRequest();
        wrongPath.setRequestURI("/oauth2/token");
        assertThat(converter.convert(wrongPath)).isNull();

        MockHttpServletRequest wrongMethod = tokenRequest();
        wrongMethod.setMethod("GET");
        assertThat(converter.convert(wrongMethod)).isNull();

        MockHttpServletRequest differentGrant = tokenRequest();
        differentGrant.setParameter("grant_type", "refresh_token");
        assertThat(converter.convert(differentGrant)).isNull();

        String source = Files.readString(Path.of("src/main/java/com/jacolp/module/system/biz/web/authorization/"
                + "CoreAgentAuthorizationCodeTokenAuthenticationConverter.java"));
        assertThat(source).doesNotContain("getHeader", "X-Forwarded-For", "Forwarded", "getSession", "getParameter(");
    }

    @Test
    void registersConverter() {
        runner.run(context -> assertThat(context.getBeansOfType(
                CoreAgentAuthorizationCodeTokenAuthenticationConverter.class)).hasSize(1));
    }

    private static MockHttpServletRequest tokenRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth/token");
        request.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        request.addParameter("grant_type", "authorization_code");
        request.addParameter("client_id", "core_agent");
        request.addParameter("code", "opaque-authorization-code");
        request.addParameter("redirect_uri", "http://127.0.0.1:9090/oauth/callback");
        request.addParameter("code_verifier", "A".repeat(43));
        return request;
    }

    private static void assertInvalidRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(exception -> assertThat(((OAuth2AuthenticationException) exception).getError().getErrorCode())
                        .isEqualTo("invalid_request"));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentAuthorizationCodeTokenAuthenticationConverter.class)
    static class ConverterOnlyConfiguration {
    }
}
