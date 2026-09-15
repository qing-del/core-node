package com.jacolp.config;

import com.jacolp.common.security.oauth2.authorization.BusinessRouteAuthorizationPolicy;
import com.jacolp.common.security.context.BaseContext;
import com.jacolp.common.security.context.PermissionContext;
import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.common.security.oauth2.config.OAuth2Rs256CodecConfiguration;
import com.jacolp.common.security.oauth2.config.OAuth2Rs256Properties;
import com.jacolp.common.security.oauth2.key.RsaKeyMaterial;
import com.jacolp.common.security.oauth2.jwt.CoreNodeAccessTokenClaimsValidator;
import com.jacolp.common.security.oauth2.token.AccessTokenIssueRequest;
import com.jacolp.common.security.oauth2.token.IssuedAccessToken;
import com.jacolp.common.security.oauth2.token.Rs256AccessTokenIssuer;
import com.jacolp.common.security.oauth2.token.SecureOAuth2TokenGenerator;
import com.jacolp.common.core.metrics.QpsCounter;
import com.jacolp.common.security.jwt.JwtProperties;
import com.jacolp.system.application.authorization.CoreAgentBrowserAccountAuthenticator;
import com.jacolp.system.application.authorization.CoreAgentBrowserAuthenticationProvider;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationCodeIssueService;
import com.jacolp.system.application.authorization.CoreAgentConsentScopeService;
import com.jacolp.system.application.authorization.CoreAgentLogoutService;
import com.jacolp.system.application.authorization.CoreAgentPendingAuthorizationHandleGenerator;
import com.jacolp.system.application.authorization.CoreAgentRegisteredClientPolicyResolver;
import com.jacolp.system.application.authorization.EffectiveRolePermissionResolver;
import com.jacolp.system.application.authorization.OAuth2ScopeResolver;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.system.application.port.out.CoreAgentPendingAuthorizationCodeTransitionStore;
import com.jacolp.system.infrastructure.authorization.ActiveRegisteredClientRepository;
import com.jacolp.system.infrastructure.authorization.CoreAgentAuthorizationServerConfiguration;
import com.jacolp.system.infrastructure.authorization.FailClosedOAuth2AuthorizationService;
import com.jacolp.system.infrastructure.authorization.RedisCoreAgentPendingAuthorizationStore;
import com.jacolp.system.infrastructure.security.BCryptEmailLoginCodeProtector;
import com.jacolp.system.infrastructure.security.BCryptPasswordCredentialVerifier;
import com.jacolp.system.infrastructure.security.PasswordEncoder;
import com.jacolp.system.web.authorization.CoreAgentAuthorizationCodeRequestAuthenticationProvider;
import com.jacolp.system.web.authorization.CoreAgentAuthorizationCodeTokenAuthenticationConverter;
import com.jacolp.system.web.authorization.CoreAgentAuthorizationCodeTokenAuthenticationProvider;
import com.jacolp.system.web.authorization.CoreAgentAuthorizationConsentAuthenticationProvider;
import com.jacolp.system.web.authorization.CoreAgentAuthorizationEndpointAuthenticationConverter;
import com.jacolp.system.web.authorization.CoreAgentAuthorizationServerSecurityConfiguration;
import com.jacolp.system.web.authorization.CoreAgentPublicClientAuthenticationConverter;
import com.jacolp.system.web.authorization.CoreAgentPublicClientAuthenticationProvider;
import com.jacolp.system.web.authorization.CoreAgentRefreshTokenAuthenticationConverter;
import com.jacolp.system.web.authorization.CoreAgentRefreshTokenAuthenticationProvider;
import com.jacolp.system.web.controller.authorization.CoreAgentLogoutController;
import com.jacolp.common.web.config.SecurityFilterConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BusinessRouteResourceServerSecurityConfigurationTest {

    @TempDir
    Path keyDirectory;

    @Test
    void aggregateContextOrdersOAuthBusinessAndLegacyChainsWithoutRouteTakeover() {
        try (AnnotationConfigWebApplicationContext context = aggregateContext()) {
            FilterChainProxy chains = context.getBean("springSecurityFilterChain", FilterChainProxy.class);
            assertThat(chains.getFilterChains()).hasSize(3);
            List<SecurityFilterChain> ordered = chains.getFilterChains();

            assertThat(ordered.get(0).matches(request(HttpMethod.GET, "/oauth2/authorize"))).isTrue();
            assertThat(ordered.get(1).matches(request(HttpMethod.GET, "/oauth2/authorize"))).isFalse();
            assertThat(ordered.get(0).matches(request(HttpMethod.POST, "/oauth/token"))).isTrue();
            assertThat(ordered.get(1).matches(request(HttpMethod.POST, "/oauth/token"))).isFalse();

            assertThat(ordered.get(0).matches(request(HttpMethod.GET, "/user/note/9"))).isFalse();
            assertThat(ordered.get(1).matches(request(HttpMethod.GET, "/user/note/9"))).isTrue();
            assertThat(ordered.get(1).matches(request(HttpMethod.POST, "/auth/logout"))).isTrue();

            for (String exception : List.of("POST /user/user/register", "POST /user/user/resend-activation",
                    "GET /user/user/active/token", "POST /user/user/active-code", "GET /unrelated")) {
                String[] parts = exception.split(" ", 2);
                MockHttpServletRequest request = request(HttpMethod.valueOf(parts[0]), parts[1]);
                assertThat(ordered.get(0).matches(request)).isFalse();
                assertThat(ordered.get(1).matches(request)).isFalse();
                assertThat(ordered.get(2).matches(request)).isTrue();
            }
        }
    }

    @Test
    void aggregateContextStartsWithExternalPemAndUsesTheRealRs256Codec() throws Exception {
        KeyPair signingPair = rsaPair();
        Path privateKey = pemFile("phase5-private.pem", "PRIVATE KEY", signingPair.getPrivate().getEncoded());
        Path publicKey = pemFile("phase5-public.pem", "PUBLIC KEY", signingPair.getPublic().getEncoded());

        try (AnnotationConfigWebApplicationContext context = aggregateContext(privateKey, publicKey)) {
            List<SecurityFilterChain> chains = context.getBean("springSecurityFilterChain", FilterChainProxy.class)
                    .getFilterChains();
            assertThat(chains).hasSize(3);
            assertThat(chains.get(0).matches(request(HttpMethod.POST, "/oauth/token"))).isTrue();
            assertThat(chains.get(1).matches(request(HttpMethod.GET, "/user/note/9"))).isTrue();
            assertThat(chains.get(2).matches(request(HttpMethod.GET, "/unrelated"))).isTrue();

            RsaKeyMaterial keyMaterial = context.getBean(RsaKeyMaterial.class);
            assertThat(keyMaterial.publicKey().getModulus().bitLength()).isGreaterThanOrEqualTo(2048);
            IssuedAccessToken issued = context.getBean(Rs256AccessTokenIssuer.class).issue(
                    new AccessTokenIssueRequest(42, "user", "password", "alice", "USER",
                            Set.of("note:read"), Duration.ofMinutes(5)));
            Jwt decoded = context.getBean(JwtDecoder.class).decode(issued.tokenValue());
            assertThat(decoded.getSubject()).isEqualTo("42");
            assertThat(decoded.getClaimAsString("client_id")).isEqualTo("user");
        }
    }

    @Test
    void aggregateContextStartsCoreAgentCodeIssuerWithoutClockBean() {
        try (AnnotationConfigWebApplicationContext context = aggregateContext()) {
            assertThat(context.getBeansOfType(Clock.class)).isEmpty();
            assertThat(context.getBean(CoreAgentAuthorizationCodeIssueService.class)).isNotNull();
            assertThat(AopUtils.isCglibProxy(context.getBean(RedisCoreAgentPendingAuthorizationStore.class))).isTrue();
            assertThat(context.getBean(BCryptPasswordCredentialVerifier.class)).isNotNull();
            assertThat(context.getBean(BCryptEmailLoginCodeProtector.class)).isNotNull();
        }
    }

    @Test
    void orderTwoMatchesOnlyCatalogueRoutesAndLeavesAllExceptionsAndOAuthAlone() throws Exception {
        Method chain = BusinessRouteResourceServerSecurityConfiguration.class.getDeclaredMethod(
                "businessRouteResourceServerSecurityFilterChain", org.springframework.security.config.annotation.web.builders.HttpSecurity.class,
                RequestMatcher.class, RequestMatcher.class,
                BusinessRouteAuthorizationPolicy.class,
                JwtDecoder.class, CoreNodeAccessTokenClaimsValidator.class);
        assertThat(AnnotationUtils.findAnnotation(chain, Order.class).value()).isEqualTo(2);

        BusinessRouteScopeCatalogConfiguration catalogue = new BusinessRouteScopeCatalogConfiguration();
        RequestMatcher matcher = catalogue.businessRouteRequestMatcher();
        RequestMatcher resourceServerMatcher = catalogue.businessResourceServerRequestMatcher(matcher,
                catalogue.internalLogoutRequestMatcher());
        assertThat(matcher.matches(request(HttpMethod.GET, "/user/note/9"))).isTrue();
        assertThat(matcher.matches(request(HttpMethod.PUT, "/admin/user/user"))).isTrue();
        assertThat(matcher.matches(request(HttpMethod.POST, "/auth/logout"))).isFalse();
        assertThat(resourceServerMatcher.matches(request(HttpMethod.POST, "/auth/logout"))).isTrue();
        assertThat(resourceServerMatcher.matches(request(HttpMethod.GET, "/auth/logout"))).isFalse();
        for (String exception : List.of("POST /user/user/register", "POST /user/user/resend-activation",
                "GET /user/user/active/token", "POST /user/user/active-code")) {
            String[] parts = exception.split(" ", 2);
            assertThat(resourceServerMatcher.matches(request(HttpMethod.valueOf(parts[0]), parts[1]))).isFalse();
        }
        assertThat(resourceServerMatcher.matches(request(HttpMethod.POST, "/oauth/token"))).isFalse();
    }

    @Test
    void resourceServerEnforcesScopesClientBoundaryClaimsAndCompatibilityContexts() throws Exception {
        try (AnnotationConfigWebApplicationContext context = enabledContext()) {
            FilterChainProxy chain = context.getBean("springSecurityFilterChain", FilterChainProxy.class);
            assertThat(chain.getFilterChains()).hasSize(1);
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(chain).build();

            mvc.perform(get("/user/note/9").header("Authorization", "Bearer user-read"))
                    .andExpect(status().isOk()).andExpect(content().string("42:false"));
            mvc.perform(get("/user/note/9").header("Authorization", "Bearer user-wildcard"))
                    .andExpect(status().isOk()).andExpect(content().string("42:false"));
            mvc.perform(get("/user/note/9").header("Authorization", "Bearer user-creator"))
                    .andExpect(status().isOk()).andExpect(content().string("42:true"));
            mvc.perform(put("/admin/user/user").header("Authorization", "Bearer admin-creator"))
                    .andExpect(status().isOk()).andExpect(content().string("42:true"));
            mvc.perform(post("/auth/logout").header("Authorization", "Bearer user-read"))
                    .andExpect(status().isOk()).andExpect(content().string("42:false"));
            mvc.perform(post("/auth/logout").header("Authorization", "Bearer admin-creator"))
                    .andExpect(status().isOk()).andExpect(content().string("42:true"));

            mvc.perform(get("/user/note/9").header("Authorization", "Bearer core-agent"))
                    .andExpect(status().isForbidden()).andExpect(content().string(org.hamcrest.Matchers.containsString("无权访问")));
            mvc.perform(get("/user/note/9").header("Authorization", "Bearer user-no-scope"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/admin/user/user").header("Authorization", "Bearer user-manage"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/auth/logout").header("Authorization", "Bearer core-agent"))
                    .andExpect(status().isForbidden());

            mvc.perform(get("/user/note/9")).andExpect(status().isUnauthorized())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("认证失败")));
            mvc.perform(post("/auth/logout")).andExpect(status().isUnauthorized());
            mvc.perform(post("/auth/logout").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
            mvc.perform(get("/user/note/9").header("Authorization", "Bearer invalid"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/user/note/9").header("Authorization", "Bearer malformed"))
                    .andExpect(status().isUnauthorized());

            mvc.perform(post("/user/user/active-code")).andExpect(status().isOk()).andExpect(content().string("activation"));
            mvc.perform(post("/oauth/token")).andExpect(status().isOk()).andExpect(content().string("oauth"));
        }
    }

    @Test
    void alwaysCreatesTheOrderTwoBusinessChain() {
        try (AnnotationConfigWebApplicationContext context = enabledContext()) {
            assertThat(context.containsBean("businessRouteResourceServerSecurityFilterChain")).isTrue();
            assertThat(context.getBean("springSecurityFilterChain", FilterChainProxy.class).getFilterChains()).hasSize(1);
        }
    }

    private static AnnotationConfigWebApplicationContext enabledContext() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestWebConfiguration.class, BusinessRouteScopeCatalogConfiguration.class,
                BusinessRouteResourceServerSecurityConfiguration.class);
        context.refresh();
        return context;
    }

    private static AnnotationConfigWebApplicationContext aggregateContext() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestWebConfiguration.class, AggregateOAuthDependencies.class, AggregateCodeIssuerDependencies.class,
                SecurityFilterConfiguration.class, CoreAgentAuthorizationServerConfiguration.class,
                CoreAgentAuthorizationServerSecurityConfiguration.class, CoreAgentLogoutController.class,
                BusinessRouteScopeCatalogConfiguration.class, BusinessRouteResourceServerSecurityConfiguration.class);
        context.refresh();
        return context;
    }

    private static AnnotationConfigWebApplicationContext aggregateContext(Path privateKey, Path publicKey) {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "jacolp.oauth2.rs256.private-key-location", privateKey.toUri().toString(),
                "jacolp.oauth2.rs256.public-key-location", publicKey.toUri().toString())));
        context.register(RealPemWebConfiguration.class, AggregateOAuthDependencies.class,
                OAuth2Rs256CodecConfiguration.class, SecurityFilterConfiguration.class,
                CoreAgentAuthorizationServerConfiguration.class, CoreAgentAuthorizationServerSecurityConfiguration.class,
                CoreAgentLogoutController.class, BusinessRouteScopeCatalogConfiguration.class,
                BusinessRouteResourceServerSecurityConfiguration.class);
        context.refresh();
        return context;
    }

    private static MockHttpServletRequest request(HttpMethod method, String path) {
        return new MockHttpServletRequest(method.name(), path);
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class TestWebConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> switch (token) {
                case "invalid" -> throw new BadJwtException("invalid signature");
                case "malformed" -> jwt("user", "USER", List.of("note:read")).claim("roles", List.of("USER", "ADMIN")).build();
                case "user-read" -> jwt("user", "USER", List.of("note:read")).build();
                case "user-wildcard" -> jwt("user", "USER", List.of("*:read")).build();
                case "user-creator" -> jwt("user", "CREATOR", List.of("note:read")).build();
                case "user-no-scope" -> jwt("user", "USER", List.of("media:read")).build();
                case "user-manage" -> jwt("user", "USER", List.of("*:manage")).build();
                case "admin-creator" -> jwt("admin", "CREATOR", List.of("account:manage")).build();
                case "core-agent" -> jwt("core_agent", "USER", List.of("note:read")).build();
                default -> throw new BadJwtException("unknown token");
            };
        }

        @Bean
        CoreNodeAccessTokenClaimsValidator coreNodeAccessTokenClaimsValidator() {
            return new CoreNodeAccessTokenClaimsValidator();
        }

        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableConfigurationProperties(OAuth2Rs256Properties.class)
    static class RealPemWebConfiguration {

        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AggregateOAuthDependencies {
        @Bean StringRedisTemplate redis() { return org.mockito.Mockito.mock(StringRedisTemplate.class); }
        @Bean JwtProperties jwtProperties() {
            JwtProperties properties = new JwtProperties();
            properties.setActiveSecretKey("test-active-secret");
            return properties;
        }
        @Bean QpsCounter qpsCounter() { return org.mockito.Mockito.mock(QpsCounter.class); }
        @Bean ActiveRegisteredClientRepository registeredClientRepository() { return org.mockito.Mockito.mock(ActiveRegisteredClientRepository.class); }
        @Bean FailClosedOAuth2AuthorizationService authorizationService() { return org.mockito.Mockito.mock(FailClosedOAuth2AuthorizationService.class); }
        @Bean OAuth2AuthorizationConsentService authorizationConsentService() { return org.mockito.Mockito.mock(OAuth2AuthorizationConsentService.class); }
        @Bean CoreAgentLogoutService coreAgentLogoutService() { return org.mockito.Mockito.mock(CoreAgentLogoutService.class); }
        @Bean CoreAgentPublicClientAuthenticationConverter publicClientAuthenticationConverter() { return org.mockito.Mockito.mock(CoreAgentPublicClientAuthenticationConverter.class); }
        @Bean CoreAgentPublicClientAuthenticationProvider publicClientAuthenticationProvider() { return org.mockito.Mockito.mock(CoreAgentPublicClientAuthenticationProvider.class); }
        @Bean CoreAgentAuthorizationEndpointAuthenticationConverter authorizationEndpointAuthenticationConverter() { return org.mockito.Mockito.mock(CoreAgentAuthorizationEndpointAuthenticationConverter.class); }
        @Bean CoreAgentAuthorizationCodeRequestAuthenticationProvider authorizationCodeRequestAuthenticationProvider() { return org.mockito.Mockito.mock(CoreAgentAuthorizationCodeRequestAuthenticationProvider.class); }
        @Bean CoreAgentAuthorizationConsentAuthenticationProvider authorizationConsentAuthenticationProvider() { return org.mockito.Mockito.mock(CoreAgentAuthorizationConsentAuthenticationProvider.class); }
        @Bean CoreAgentAuthorizationCodeTokenAuthenticationConverter authorizationCodeTokenAuthenticationConverter() { return org.mockito.Mockito.mock(CoreAgentAuthorizationCodeTokenAuthenticationConverter.class); }
        @Bean CoreAgentRefreshTokenAuthenticationConverter refreshTokenAuthenticationConverter() { return org.mockito.Mockito.mock(CoreAgentRefreshTokenAuthenticationConverter.class); }
        @Bean CoreAgentAuthorizationCodeTokenAuthenticationProvider authorizationCodeTokenAuthenticationProvider() { return org.mockito.Mockito.mock(CoreAgentAuthorizationCodeTokenAuthenticationProvider.class); }
        @Bean CoreAgentRefreshTokenAuthenticationProvider refreshTokenAuthenticationProvider() { return org.mockito.Mockito.mock(CoreAgentRefreshTokenAuthenticationProvider.class); }
        @Bean CoreAgentBrowserAccountAuthenticator browserAccountAuthenticator() { return org.mockito.Mockito.mock(CoreAgentBrowserAccountAuthenticator.class); }
        @Bean CoreAgentBrowserAuthenticationProvider browserAuthenticationProvider(CoreAgentBrowserAccountAuthenticator authenticator) {
            return new CoreAgentBrowserAuthenticationProvider(authenticator);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import({CoreAgentAuthorizationCodeIssueService.class, RedisCoreAgentPendingAuthorizationStore.class,
            PasswordEncoder.class, BCryptPasswordCredentialVerifier.class, BCryptEmailLoginCodeProtector.class})
    static class AggregateCodeIssuerDependencies {
        @Bean
        static PersistenceExceptionTranslationPostProcessor persistenceExceptionTranslationPostProcessor() {
            PersistenceExceptionTranslationPostProcessor processor = new PersistenceExceptionTranslationPostProcessor();
            processor.setProxyTargetClass(true);
            return processor;
        }

        @Bean SecureOAuth2TokenGenerator tokenGenerator() { return new SecureOAuth2TokenGenerator(); }
        @Bean CoreAgentRegisteredClientPolicyResolver policyResolver() {
            return org.mockito.Mockito.mock(CoreAgentRegisteredClientPolicyResolver.class);
        }
        @Bean AuthorizationAccountRepository accountRepository() {
            return org.mockito.Mockito.mock(AuthorizationAccountRepository.class);
        }
        @Bean AccountGrantTypeResolver accountGrantTypeResolver() {
            return new AccountGrantTypeResolver(AccountGrantTypeResolver.requiredDefaultGrantTypes());
        }
        @Bean EffectiveRolePermissionResolver rolePermissionResolver() {
            return org.mockito.Mockito.mock(EffectiveRolePermissionResolver.class);
        }
        @Bean CoreAgentConsentScopeService consentScopeService() {
            return new CoreAgentConsentScopeService(new OAuth2ScopeResolver());
        }
        @Bean CoreAgentPendingAuthorizationHandleGenerator pendingHandleGenerator(SecureOAuth2TokenGenerator generator) {
            return new CoreAgentPendingAuthorizationHandleGenerator(generator);
        }
        @Bean CoreAgentPendingAuthorizationCodeTransitionStore transitionStore() {
            return org.mockito.Mockito.mock(CoreAgentPendingAuthorizationCodeTransitionStore.class);
        }
    }

    @RestController
    static class TestController {

        @GetMapping("/user/note/{id}")
        String userNote() {
            return BaseContext.getCurrentId() + ":" + PermissionContext.isAdmin();
        }

        @PutMapping("/admin/user/user")
        String adminUser() {
            return BaseContext.getCurrentId() + ":" + PermissionContext.isAdmin();
        }

        @PostMapping("/user/user/active-code")
        String activation() {
            return "activation";
        }

        @PostMapping("/oauth/token")
        String oauth() {
            return "oauth";
        }

        @PostMapping("/auth/logout")
        String logout() {
            return BaseContext.getCurrentId() + ":" + PermissionContext.isAdmin();
        }
    }

    private static Jwt.Builder jwt(String clientId, String role, List<String> scopes) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("42")
                .claim("username", "alice")
                .claim("client_id", clientId)
                .claim("grant_type", clientId.equals("core_agent") ? "authorization_code" : "password")
                .claim("roles", List.of(role))
                .claim("scope", scopes)
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T01:00:00Z"));
    }

    private static KeyPair rsaPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private Path pemFile(String name, String type, byte[] encoded) throws Exception {
        return Files.writeString(keyDirectory.resolve(name), "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                + "\n-----END " + type + "-----\n");
    }
}
