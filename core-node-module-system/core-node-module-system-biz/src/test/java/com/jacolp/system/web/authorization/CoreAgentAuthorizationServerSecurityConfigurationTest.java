package com.jacolp.system.web.authorization;

import com.jacolp.common.core.metrics.QpsCounter;
import com.jacolp.common.security.jwt.JwtProperties;
import com.jacolp.system.application.port.out.CoreAgentPendingAuthorizationStore;
import com.jacolp.system.infrastructure.authorization.ActiveRegisteredClientRepository;
import com.jacolp.system.infrastructure.authorization.CoreAgentAuthorizationServerConfiguration;
import com.jacolp.system.infrastructure.authorization.FailClosedOAuth2AuthorizationService;
import com.jacolp.system.web.controller.authorization.CoreAgentLogoutController;
import com.jacolp.common.web.config.SecurityFilterConfiguration;
import com.jacolp.system.application.authorization.CoreAgentBrowserAccountAuthenticator;
import com.jacolp.system.application.authorization.CoreAgentBrowserAuthenticationProvider;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationCodeIssueRejectedException;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationCodeIssueService;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationConsentService;
import com.jacolp.system.application.authorization.CoreAgentRegisteredClientPolicyResolver;
import com.jacolp.system.application.authorization.EffectiveRolePermissionResolver;
import com.jacolp.system.application.authorization.CoreAgentLogoutRejectedException;
import com.jacolp.system.application.authorization.CoreAgentLogoutService;
import com.jacolp.system.application.authorization.model.CoreAgentBrowserPrincipal;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationConsentDecision;
import com.jacolp.system.application.authorization.model.CoreAgentConsentScopeOptions;
import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationState;
import com.jacolp.system.application.authorization.model.CoreAgentPreparedPendingAuthorization;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationCode;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationPendingHandle;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.lang.reflect.Method;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

class CoreAgentAuthorizationServerSecurityConfigurationTest {

    @Test
    void exactMatcherIncludesOnlyTheFivePhaseFourRoutes() {
        assertThat(matches("/oauth2/authorize")).isTrue();
        assertThat(matches("/oauth/token")).isTrue();
        assertThat(matches("/oauth/login")).isTrue();
        assertThat(matches("/oauth/consent")).isTrue();
        assertThat(matches("/oauth/logout")).isTrue();

        assertThat(matches("/oauth2/revoke")).isFalse();
        assertThat(matches("/oauth2/introspect")).isFalse();
        assertThat(matches("/.well-known/oauth-authorization-server")).isFalse();
        assertThat(matches("/oauth2/jwks")).isFalse();
        assertThat(matches("/oauth2/device_authorization")).isFalse();
        assertThat(matches("/oauth2/par")).isFalse();
        assertThat(matches("/user/protected")).isFalse();
    }

    @Test
    void sourceNeverUsesTheBroadSasEndpointMatcherOrAddsAuthorizationPersistence() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/jacolp/system/web/authorization/"
                + "CoreAgentAuthorizationServerSecurityConfiguration.java"));

        assertThat(source).doesNotContain("getEndpointsMatcher", "OAuth2AuthorizationService", "OAuth2TokenGenerator",
                "InMemoryOAuth2AuthorizationService", "JdbcOAuth2AuthorizationService");
        assertThat(source).contains("securityMatcher(browserAndTokenRoutes)", "postPath(TOKEN_PATH)",
                "postPath(LOGOUT_PATH)", "browserAuthorizationCsrfFilter", "oauth2ResourceServer",
                "coreAgentLogoutBearerTokenResolver");
    }

    @Test
    void isolatedPhaseFourContextUsesOrderOneAndOrderThreeFallbackWithBrowserCsrfProtection() throws Exception {
        try (AnnotationConfigWebApplicationContext context = enabledContext()) {
            FilterChainProxy chains = context.getBean("springSecurityFilterChain", FilterChainProxy.class);
            assertThat(chains.getFilterChains()).hasSize(2);
            assertOrder(CoreAgentAuthorizationServerSecurityConfiguration.class,
                    "coreAgentAuthorizationServerSecurityFilterChain", 1);
            assertOrder(SecurityFilterConfiguration.class, "securityFilterChain", 3);
            assertThat(chains.getFilters("/oauth2/authorize")).anyMatch(CsrfFilter.class::isInstance);
            assertThat(chains.getFilters("/oauth2/revoke")).isNotEmpty();

            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(chains).build();
            mvc.perform(post("/oauth2/authorize"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/oauth2/authorize").param("_csrf", "wrong-token"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/oauth/login"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/oauth/login").param("_csrf", "wrong-token"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/oauth/token").param("grant_type", "unsupported"))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
            mvc.perform(post("/oauth/logout"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/oauth2/revoke"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void disablingLoginCsrfDoesNotDisableAuthorizationCsrf() throws Exception {
        try (AnnotationConfigWebApplicationContext context = disabledCsrfContext()) {
            CoreAgentBrowserAccountAuthenticator accountAuthenticator = context.getBean(CoreAgentBrowserAccountAuthenticator.class);
            when(accountAuthenticator.authenticate("alice", "password"))
                    .thenReturn(new CoreAgentBrowserPrincipal(7L, "alice", 3L, "USER", 1));
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean("springSecurityFilterChain", FilterChainProxy.class)).build();

            mvc.perform(post("/oauth/login").param("username", "alice").param("password", "password"))
                    .andExpect(status().is3xxRedirection());
            verify(accountAuthenticator).authenticate("alice", "password");
            mvc.perform(post("/oauth2/authorize"))
                    .andExpect(status().isForbidden());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void logoutRequiresOnlyRs256ValidatedBearerAndDelegatesOnlyTheCoreAgentToken() throws Exception {
        try (AnnotationConfigWebApplicationContext context = enabledContext()) {
            JwtDecoder decoder = context.getBean(JwtDecoder.class);
            CoreAgentLogoutService logoutService = context.getBean(CoreAgentLogoutService.class);
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean("springSecurityFilterChain", FilterChainProxy.class)).build();

            mvc.perform(post("/oauth/logout")).andExpect(status().isUnauthorized());
            verify(decoder, never()).decode(org.mockito.ArgumentMatchers.anyString());
            verify(logoutService, never()).logout();

            when(decoder.decode("bad-token")).thenThrow(new BadJwtException("invalid"));
            when(decoder.decode("hs256-token")).thenThrow(new BadJwtException("invalid"));
            when(decoder.decode("revoked-token")).thenThrow(new BadJwtException("invalid"));
            mvc.perform(post("/oauth/logout").header("Authorization", "Bearer bad-token"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/oauth/logout").header("Authorization", "Bearer hs256-token"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/oauth/logout").header("Authorization", "Bearer revoked-token"))
                    .andExpect(status().isUnauthorized());
            verify(logoutService, never()).logout();

            when(decoder.decode("valid-core-agent-token")).thenReturn(jwt("core_agent"));
            mvc.perform(post("/oauth/logout").header("Authorization", "Bearer valid-core-agent-token"))
                    .andExpect(status().isOk());
            verify(logoutService).logout();

            Mockito.doThrow(new CoreAgentLogoutRejectedException()).when(logoutService).logout();
            when(decoder.decode("wrong-client-token")).thenReturn(jwt("user"));
            assertThatThrownBy(() -> mvc.perform(post("/oauth/logout")
                    .header("Authorization", "Bearer wrong-client-token")))
                    .hasRootCauseInstanceOf(CoreAgentLogoutRejectedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void unauthenticatedAuthorizeIsSavedThenValidProjectFormLoginReturnsToIt() throws Exception {
        try (AnnotationConfigWebApplicationContext context = enabledContext()) {
            CoreAgentBrowserAccountAuthenticator accountAuthenticator = context.getBean(CoreAgentBrowserAccountAuthenticator.class);
            when(accountAuthenticator.authenticate("alice", "password"))
                    .thenReturn(new CoreAgentBrowserPrincipal(7L, "alice", 3L, "USER", 1));
            FilterChainProxy chains = context.getBean("springSecurityFilterChain", FilterChainProxy.class);
            org.springframework.security.oauth2.server.authorization.web.OAuth2AuthorizationEndpointFilter endpointFilter =
                    chains.getFilters("/oauth2/authorize").stream()
                            .filter(org.springframework.security.oauth2.server.authorization.web.OAuth2AuthorizationEndpointFilter.class::isInstance)
                            .map(org.springframework.security.oauth2.server.authorization.web.OAuth2AuthorizationEndpointFilter.class::cast)
                            .findFirst().orElseThrow();
            assertThat(ReflectionTestUtils.getField(endpointFilter, "authenticationDetailsSource"))
                    .isInstanceOf(CoreAgentAuthorizationEndpointAuthenticationDetailsSource.class);
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(chains).build();
            MockHttpSession session = new MockHttpSession();

            mvc.perform(get("/oauth2/authorize").param("response_type", "code").param("client_id", "core_agent")
                            .session(session))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/oauth/login"));

            CsrfTokenRepository csrfRepository = context.getBean(CsrfTokenRepository.class);
            MockHttpServletRequest csrfRequest = new MockHttpServletRequest("GET", "/oauth/login");
            csrfRequest.setSession(session);
            CsrfToken csrfToken = csrfRepository.generateToken(csrfRequest);
            csrfRepository.saveToken(csrfToken, csrfRequest, new MockHttpServletResponse());

            mvc.perform(post("/oauth/login").session(session).param("username", "alice")
                            .param("password", "password").param(csrfToken.getParameterName(), csrfToken.getToken()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/oauth2/authorize**"));
            verify(accountAuthenticator).authenticate("alice", "password");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void restoredAuthorizeUsesTheProjectRequestProviderInsteadOfTheFailClosedSasBootstrapProvider() throws Exception {
        try (AnnotationConfigWebApplicationContext context = realProviderContext()) {
            CoreAgentBrowserAccountAuthenticator accountAuthenticator = context.getBean(CoreAgentBrowserAccountAuthenticator.class);
            CoreAgentRegisteredClientPolicyResolver policyResolver = context.getBean(CoreAgentRegisteredClientPolicyResolver.class);
            EffectiveRolePermissionResolver roleResolver = context.getBean(EffectiveRolePermissionResolver.class);
            CoreAgentAuthorizationConsentService consentService = context.getBean(CoreAgentAuthorizationConsentService.class);
            CoreAgentAuthorizationCodeIssueService issueService = context.getBean(CoreAgentAuthorizationCodeIssueService.class);
            ActiveRegisteredClientRepository registeredClientRepository = context.getBean(ActiveRegisteredClientRepository.class);
            FailClosedOAuth2AuthorizationService authorizationService = context.getBean(FailClosedOAuth2AuthorizationService.class);
            when(accountAuthenticator.authenticate("alice", "password"))
                    .thenReturn(new CoreAgentBrowserPrincipal(7L, "alice", 3L, "USER", 1));
            CoreAgentRegisteredClientPolicy policy = new CoreAgentRegisteredClientPolicy("core-agent-id", "core_agent",
                    "http://127.0.0.1:9090/oauth/callback", Set.of("note:read"), Set.of("note:read"),
                    "0.0.0.0/0", java.time.Duration.ofHours(1), java.time.Duration.ofHours(24),
                    java.time.Duration.ofMinutes(10));
            when(registeredClientRepository.findByClientId("core_agent")).thenReturn(
                    org.springframework.security.oauth2.server.authorization.client.RegisteredClient.withId("core-agent-id")
                            .clientId("core_agent")
                            .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.NONE)
                            .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                            .redirectUri(policy.redirectUri())
                            .scope("note:read")
                            .build());
            when(policyResolver.resolve("core_agent")).thenReturn(policy);
            when(roleResolver.resolve(3L)).thenReturn(new EffectiveRolePermissions(3L, "USER", 1, List.of("note:read")));
            CoreAgentConsentScopeOptions options = new CoreAgentConsentScopeOptions(List.of("note:read"),
                    List.of("note:read"), List.of(), List.of());
            when(consentService.prepare(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.nullable(List.class)))
                    .thenReturn(new CoreAgentAuthorizationConsentDecision(options, true, List.of()));
            when(issueService.createPending(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                    .thenAnswer(invocation -> prepared(invocation.getArgument(1)));
            when(issueService.convertPending(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(new IssuedCoreAgentAuthorizationCode("A".repeat(43), Instant.parse("2026-08-15T01:10:00Z")));

            FilterChainProxy chains = context.getBean("springSecurityFilterChain", FilterChainProxy.class);
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(chains).build();
            MockHttpSession session = new MockHttpSession();
            org.springframework.security.web.savedrequest.RequestCache requestCache = context.getBean(
                    org.springframework.security.web.savedrequest.RequestCache.class);
            MockHttpServletRequest savedAuthorizeRequest = new MockHttpServletRequest("GET", "/oauth2/authorize");
            savedAuthorizeRequest.setSession(session);
            savedAuthorizeRequest.setParameter("response_type", "code");
            savedAuthorizeRequest.setParameter("client_id", "core_agent");
            savedAuthorizeRequest.setParameter("redirect_uri", policy.redirectUri());
            savedAuthorizeRequest.setParameter("code_challenge", "A".repeat(43));
            savedAuthorizeRequest.setParameter("code_challenge_method", "S256");
            savedAuthorizeRequest.setParameter("state", "opaque-state");
            requestCache.saveRequest(savedAuthorizeRequest, new MockHttpServletResponse());
            CsrfTokenRepository csrfRepository = context.getBean(CsrfTokenRepository.class);
            MockHttpServletRequest csrfRequest = new MockHttpServletRequest("GET", "/oauth/login");
            csrfRequest.setSession(session);
            CsrfToken csrfToken = csrfRepository.generateToken(csrfRequest);
            csrfRepository.saveToken(csrfToken, csrfRequest, new MockHttpServletResponse());
            mvc.perform(post("/oauth/login").session(session).param("username", "alice").param("password", "password")
                            .param(csrfToken.getParameterName(), csrfToken.getToken()))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/oauth2/authorize**"));

            org.springframework.test.web.servlet.MvcResult restored = mvc.perform(get("/oauth2/authorize")
                            .queryParam("response_type", "code").queryParam("client_id", "core_agent")
                            .queryParam("redirect_uri", policy.redirectUri()).queryParam("code_challenge", "A".repeat(43))
                            .queryParam("code_challenge_method", "S256").queryParam("state", "opaque-state")
                            .session(session)).andReturn();
            verify(issueService).createPending(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            assertThat(restored.getResponse().getStatus())
                    .withFailMessage("response body=%s, redirect=%s", restored.getResponse().getContentAsString(),
                            restored.getResponse().getRedirectedUrl())
                    .isBetween(300, 399);
            assertThat(restored.getResponse().getRedirectedUrl()).startsWith("http://localhost/oauth/consent?");
            when(consentService.prepare(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.nullable(List.class)))
                    .thenThrow(new CoreAgentAuthorizationCodeIssueRejectedException());
            org.springframework.test.web.servlet.MvcResult rejected = mvc.perform(get("/oauth2/authorize")
                            .queryParam("response_type", "code").queryParam("client_id", "core_agent")
                            .queryParam("redirect_uri", policy.redirectUri()).queryParam("code_challenge", "A".repeat(43))
                            .queryParam("code_challenge_method", "S256").queryParam("state", "opaque-state")
                            .session(session)).andReturn();
            assertThat(rejected.getResponse().getStatus()).isBetween(300, 399);
            assertThat(rejected.getResponse().getRedirectedUrl())
                    .startsWith("http://127.0.0.1:9090/oauth/callback?")
                    .contains("error=access_denied");
            verifyNoInteractions(authorizationService);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static CoreAgentPreparedPendingAuthorization prepared(String sessionId) {
        Instant expiresAt = Instant.parse("2036-08-15T01:10:00Z");
        return new CoreAgentPreparedPendingAuthorization(new IssuedCoreAgentAuthorizationPendingHandle("A".repeat(43), expiresAt),
                new CoreAgentPendingAuthorizationState("core_agent", "http://127.0.0.1:9090/oauth/callback", null,
                        "A".repeat(43), "S256", "opaque-state", "127.0.0.1", 7L, sessionId,
                        Instant.parse("2036-08-15T01:00:00Z"), expiresAt));
    }

    @Test
    void registersCoreAgentSecurityChain() {
        try (AnnotationConfigWebApplicationContext context = enabledContext()) {
            FilterChainProxy chains = context.getBean("springSecurityFilterChain", FilterChainProxy.class);
            assertThat(chains.getFilterChains()).hasSize(2);
            assertThat(chains.getFilters("/oauth2/authorize")).anyMatch(CsrfFilter.class::isInstance);
            assertThat(chains.getFilters("/oauth2/revoke")).isNotEmpty();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static boolean matches(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.GET.name(), path);
        return CoreAgentAuthorizationServerSecurityConfiguration.exactCoreAgentRoutes().matches(request);
    }

    private static Jwt jwt(String clientId) {
        Instant issuedAt = Instant.parse("2026-08-12T02:00:00Z");
        return new Jwt("fixture-token", issuedAt, issuedAt.plusSeconds(3600), Map.of("alg", "RS256"), Map.of(
                "sub", "7", "client_id", clientId, "jti", "0123456789abcdefghijkl",
                "iss", "core-node", "aud", List.of("core-node")));
    }

    private static void assertOrder(Class<?> type, String methodName, int expected) throws NoSuchMethodException {
        Method method = List.of(type.getDeclaredMethods()).stream().filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
        assertThat(method.getAnnotation(Order.class)).isNotNull();
        assertThat(method.getAnnotation(Order.class).value()).isEqualTo(expected);
    }

    private static AnnotationConfigWebApplicationContext enabledContext() {
        return configuredContext(true, EnabledConfiguration.class);
    }

    private static AnnotationConfigWebApplicationContext disabledCsrfContext() {
        return configuredContext(false, EnabledConfiguration.class);
    }

    private static AnnotationConfigWebApplicationContext realProviderContext() {
        return configuredContext(true, RealProviderEnabledConfiguration.class);
    }

    private static AnnotationConfigWebApplicationContext configuredContext(boolean csrfEnabled,
                                                                           Class<?> configuration) {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("jacolp.oauth2.browser-login.csrf-enabled", Boolean.toString(csrfEnabled))));
        context.register(configuration);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityFilterConfiguration.class, CoreAgentAuthorizationServerConfiguration.class,
            CoreAgentAuthorizationServerSecurityConfiguration.class, CoreAgentLogoutController.class})
    static class EnabledConfiguration {
        @Bean StringRedisTemplate redis() { return mock(StringRedisTemplate.class); }
        @Bean
        JwtProperties jwtProperties() {
            JwtProperties properties = new JwtProperties();
            properties.setActiveSecretKey("test-active-secret");
            return properties;
        }
        @Bean
        QpsCounter qpsCounter() { return mock(QpsCounter.class); }
        @Bean ActiveRegisteredClientRepository registeredClientRepository() { return mock(ActiveRegisteredClientRepository.class); }
        @Bean FailClosedOAuth2AuthorizationService authorizationService() { return mock(FailClosedOAuth2AuthorizationService.class); }
        @Bean OAuth2AuthorizationConsentService authorizationConsentService() { return mock(OAuth2AuthorizationConsentService.class); }
        @Bean JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }
        @Bean CoreAgentLogoutService coreAgentLogoutService() { return mock(CoreAgentLogoutService.class); }
        @Bean CoreAgentPublicClientAuthenticationConverter publicClientAuthenticationConverter() { return mock(CoreAgentPublicClientAuthenticationConverter.class); }
        @Bean CoreAgentPublicClientAuthenticationProvider publicClientAuthenticationProvider() { return mock(CoreAgentPublicClientAuthenticationProvider.class); }
        @Bean CoreAgentAuthorizationEndpointAuthenticationConverter authorizationEndpointAuthenticationConverter() { return mock(CoreAgentAuthorizationEndpointAuthenticationConverter.class); }
        @Bean CoreAgentAuthorizationCodeRequestAuthenticationProvider authorizationCodeRequestAuthenticationProvider() { return mock(CoreAgentAuthorizationCodeRequestAuthenticationProvider.class); }
        @Bean CoreAgentAuthorizationConsentAuthenticationProvider authorizationConsentAuthenticationProvider() { return mock(CoreAgentAuthorizationConsentAuthenticationProvider.class); }
        @Bean CoreAgentAuthorizationCodeTokenAuthenticationConverter authorizationCodeTokenAuthenticationConverter() { return mock(CoreAgentAuthorizationCodeTokenAuthenticationConverter.class); }
        @Bean CoreAgentRefreshTokenAuthenticationConverter refreshTokenAuthenticationConverter() { return mock(CoreAgentRefreshTokenAuthenticationConverter.class); }
        @Bean CoreAgentAuthorizationCodeTokenAuthenticationProvider authorizationCodeTokenAuthenticationProvider() { return mock(CoreAgentAuthorizationCodeTokenAuthenticationProvider.class); }
        @Bean CoreAgentRefreshTokenAuthenticationProvider refreshTokenAuthenticationProvider() { return mock(CoreAgentRefreshTokenAuthenticationProvider.class); }
        @Bean CoreAgentBrowserAccountAuthenticator browserAccountAuthenticator() { return mock(CoreAgentBrowserAccountAuthenticator.class); }
        @Bean CoreAgentBrowserAuthenticationProvider browserAuthenticationProvider(CoreAgentBrowserAccountAuthenticator authenticator) {
            return new CoreAgentBrowserAuthenticationProvider(authenticator);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityFilterConfiguration.class, CoreAgentAuthorizationServerConfiguration.class,
            CoreAgentAuthorizationServerSecurityConfiguration.class, CoreAgentLogoutController.class})
    static class RealProviderEnabledConfiguration {
        @Bean StringRedisTemplate redis() { return mock(StringRedisTemplate.class); }
        @Bean JwtProperties jwtProperties() { JwtProperties properties = new JwtProperties(); properties.setActiveSecretKey("test-active-secret"); return properties; }
        @Bean QpsCounter qpsCounter() { return mock(QpsCounter.class); }
        @Bean ActiveRegisteredClientRepository registeredClientRepository() { return mock(ActiveRegisteredClientRepository.class); }
        @Bean FailClosedOAuth2AuthorizationService authorizationService() { return mock(FailClosedOAuth2AuthorizationService.class); }
        @Bean OAuth2AuthorizationConsentService authorizationConsentService() { return mock(OAuth2AuthorizationConsentService.class); }
        @Bean JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }
        @Bean CoreAgentLogoutService coreAgentLogoutService() { return mock(CoreAgentLogoutService.class); }
        @Bean CoreAgentPublicClientAuthenticationConverter publicClientAuthenticationConverter() { return mock(CoreAgentPublicClientAuthenticationConverter.class); }
        @Bean CoreAgentPublicClientAuthenticationProvider publicClientAuthenticationProvider() { return mock(CoreAgentPublicClientAuthenticationProvider.class); }
        @Bean CoreAgentAuthorizationEndpointAuthenticationConverter authorizationEndpointAuthenticationConverter() { return new CoreAgentAuthorizationEndpointAuthenticationConverter(); }
        @Bean CoreAgentRegisteredClientPolicyResolver coreAgentRegisteredClientPolicyResolver() { return mock(CoreAgentRegisteredClientPolicyResolver.class); }
        @Bean EffectiveRolePermissionResolver effectiveRolePermissionResolver() { return mock(EffectiveRolePermissionResolver.class); }
        @Bean CoreAgentAuthorizationConsentService coreAgentAuthorizationConsentService() { return mock(CoreAgentAuthorizationConsentService.class); }
        @Bean CoreAgentAuthorizationCodeIssueService coreAgentAuthorizationCodeIssueService() { return mock(CoreAgentAuthorizationCodeIssueService.class); }
        @Bean HttpSessionCoreAgentPendingAuthorizationHandleStore pendingHandleStore() { return new HttpSessionCoreAgentPendingAuthorizationHandleStore(); }
        @Bean
        CoreAgentPendingAuthorizationStore pendingAuthorizationStore() { return mock(CoreAgentPendingAuthorizationStore.class); }
        @Bean CoreAgentAuthorizationCodeRequestAuthenticationProvider authorizationCodeRequestAuthenticationProvider(
                CoreAgentRegisteredClientPolicyResolver policyResolver, EffectiveRolePermissionResolver roleResolver,
                CoreAgentAuthorizationConsentService consentService, CoreAgentAuthorizationCodeIssueService issueService,
                HttpSessionCoreAgentPendingAuthorizationHandleStore handleStore, CoreAgentPendingAuthorizationStore pendingStore) {
            return new CoreAgentAuthorizationCodeRequestAuthenticationProvider(policyResolver, roleResolver, consentService, issueService, handleStore, pendingStore);
        }
        @Bean CoreAgentAuthorizationConsentAuthenticationProvider authorizationConsentAuthenticationProvider() { return mock(CoreAgentAuthorizationConsentAuthenticationProvider.class); }
        @Bean CoreAgentAuthorizationCodeTokenAuthenticationConverter authorizationCodeTokenAuthenticationConverter() { return mock(CoreAgentAuthorizationCodeTokenAuthenticationConverter.class); }
        @Bean CoreAgentRefreshTokenAuthenticationConverter refreshTokenAuthenticationConverter() { return mock(CoreAgentRefreshTokenAuthenticationConverter.class); }
        @Bean CoreAgentAuthorizationCodeTokenAuthenticationProvider authorizationCodeTokenAuthenticationProvider() { return mock(CoreAgentAuthorizationCodeTokenAuthenticationProvider.class); }
        @Bean CoreAgentRefreshTokenAuthenticationProvider refreshTokenAuthenticationProvider() { return mock(CoreAgentRefreshTokenAuthenticationProvider.class); }
        @Bean CoreAgentBrowserAccountAuthenticator browserAccountAuthenticator() { return mock(CoreAgentBrowserAccountAuthenticator.class); }
        @Bean CoreAgentBrowserAuthenticationProvider browserAuthenticationProvider(CoreAgentBrowserAccountAuthenticator authenticator) { return new CoreAgentBrowserAuthenticationProvider(authenticator); }
    }

}
