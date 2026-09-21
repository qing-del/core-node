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
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreAgentRefreshTokenAuthenticationConverterTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConverterOnlyConfiguration.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void delegatesAbsentAndExplicitScopeRequestsWhilePreservingOnlyScopePresence() {
        UsernamePasswordAuthenticationToken client = UsernamePasswordAuthenticationToken.authenticated("core_agent", null,
                java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(client);
        CoreAgentRefreshTokenAuthenticationConverter converter = new CoreAgentRefreshTokenAuthenticationConverter();

        OAuth2RefreshTokenAuthenticationToken absent = convert(converter, refreshRequest(null));
        assertThat(absent.getGrantType()).isEqualTo(AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(absent.getRefreshToken()).isEqualTo("opaque-refresh-token");
        assertThat(absent.getScopes()).isEmpty();
        assertThat(absent.getPrincipal()).isSameAs(client);
        assertDetails(absent, refreshRequest(null), false);

        OAuth2RefreshTokenAuthenticationToken explicit = convert(converter, refreshRequest("note:read sys:read"));
        assertThat(explicit.getScopes()).containsExactlyInAnyOrder("note:read", "sys:read");
        assertDetails(explicit, refreshRequest("note:read sys:read"), true);
    }

    @Test
    void officialConverterRetainsInvalidRefreshAndScopeParameterBoundaries() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("core_agent", null, java.util.List.of()));
        CoreAgentRefreshTokenAuthenticationConverter converter = new CoreAgentRefreshTokenAuthenticationConverter();

        MockHttpServletRequest missingRefresh = refreshRequest(null);
        missingRefresh.removeParameter("refresh_token");
        assertInvalidRequest(() -> converter.convert(missingRefresh));

        MockHttpServletRequest repeatedRefresh = refreshRequest(null);
        repeatedRefresh.addParameter("refresh_token", "another-token");
        assertInvalidRequest(() -> converter.convert(repeatedRefresh));

        MockHttpServletRequest blankRefresh = refreshRequest(null);
        blankRefresh.setParameter("refresh_token", " ");
        assertInvalidRequest(() -> converter.convert(blankRefresh));

        MockHttpServletRequest repeatedScope = refreshRequest("note:read");
        repeatedScope.addParameter("scope", "sys:read");
        assertInvalidRequest(() -> converter.convert(repeatedScope));
    }

    @Test
    void unsupportedPathMethodOrGrantReturnsNullAndNeverReadsForwardedHeaders() throws IOException {
        CoreAgentRefreshTokenAuthenticationConverter converter = new CoreAgentRefreshTokenAuthenticationConverter();
        MockHttpServletRequest wrongPath = refreshRequest(null);
        wrongPath.setRequestURI("/oauth2/token");
        assertThat(converter.convert(wrongPath)).isNull();

        MockHttpServletRequest wrongMethod = refreshRequest(null);
        wrongMethod.setMethod("GET");
        assertThat(converter.convert(wrongMethod)).isNull();

        MockHttpServletRequest wrongGrant = refreshRequest(null);
        wrongGrant.setParameter("grant_type", "authorization_code");
        assertThat(converter.convert(wrongGrant)).isNull();

        String source = Files.readString(Path.of("src/main/java/com/jacolp/module/system/biz/web/authorization/"
                + "CoreAgentRefreshTokenAuthenticationConverter.java"));
        assertThat(source).doesNotContain("getHeader", "X-Forwarded-For", "Forwarded", "getSession", "getParameter(");
    }

    @Test
    void redactsSocketDetailsAndRegistersConverter() {
        CoreAgentRefreshTokenRequestDetails details = new CoreAgentRefreshTokenRequestDetails("192.0.2.24", true);
        assertThat(details.toString()).contains("<redacted>").doesNotContain("192.0.2.24");
        runner.run(context -> assertThat(context.getBeansOfType(
                CoreAgentRefreshTokenAuthenticationConverter.class)).hasSize(1));
    }

    private static OAuth2RefreshTokenAuthenticationToken convert(CoreAgentRefreshTokenAuthenticationConverter converter,
                                                                  MockHttpServletRequest request) {
        Object converted = converter.convert(request);
        assertThat(converted).isInstanceOf(OAuth2RefreshTokenAuthenticationToken.class);
        return (OAuth2RefreshTokenAuthenticationToken) converted;
    }

    private static MockHttpServletRequest refreshRequest(String scope) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth/token");
        request.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        request.setRemoteAddr("192.0.2.24");
        request.addParameter("grant_type", "refresh_token");
        request.addParameter("client_id", "core_agent");
        request.addParameter("refresh_token", "opaque-refresh-token");
        if (scope != null) {
            request.addParameter("scope", scope);
        }
        return request;
    }

    private static void assertDetails(OAuth2RefreshTokenAuthenticationToken token, MockHttpServletRequest request,
                                      boolean scopePresent) {
        assertThat(token.getDetails()).isNull();
        CoreAgentRefreshTokenRequestDetails details = (CoreAgentRefreshTokenRequestDetails)
                new CoreAgentTokenEndpointAuthenticationDetailsSource().buildDetails(request);
        assertThat(details.socketRemoteAddress()).isEqualTo("192.0.2.24");
        assertThat(details.originalScopeParameterPresent()).isEqualTo(scopePresent);
        assertThat(details.toString()).contains("<redacted>").doesNotContain("192.0.2.24");
    }

    private static void assertInvalidRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(exception -> assertThat(((OAuth2AuthenticationException) exception).getError().getErrorCode())
                        .isEqualTo("invalid_request"));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentRefreshTokenAuthenticationConverter.class)
    static class ConverterOnlyConfiguration {
    }
}
