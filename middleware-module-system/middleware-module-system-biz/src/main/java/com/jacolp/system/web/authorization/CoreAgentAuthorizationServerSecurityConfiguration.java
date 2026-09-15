package com.jacolp.system.web.authorization;

import com.jacolp.system.application.authorization.CoreAgentBrowserAuthenticationProvider;
import com.jacolp.system.infrastructure.authorization.CoreAgentAuthorizationServerConfigurerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;

/**
 * Isolated browser and token security chain for the five Phase 4 CORE AGENT routes.
 *
 * <p>The lower-priority legacy chain remains responsible for every other path. In particular,
 * this chain intentionally does not use SAS's broad endpoint matcher, so metadata, JWK, device,
 * PAR, introspection, and revocation paths cannot become reachable by enabling Phase 4.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CoreAgentBrowserLoginProperties.class)
public class CoreAgentAuthorizationServerSecurityConfiguration {

    static final String AUTHORIZE_PATH = "/oauth2/authorize";
    static final String TOKEN_PATH = "/oauth/token";
    static final String LOGIN_PATH = "/oauth/login";
    static final String CONSENT_PATH = "/oauth/consent";
    static final String LOGOUT_PATH = "/oauth/logout";

    @Bean
    CsrfTokenRepository coreAgentBrowserCsrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    CsrfTokenRequestHandler coreAgentBrowserCsrfTokenRequestHandler() {
        return new CsrfTokenRequestAttributeHandler();
    }

    @Bean
    RequestCache coreAgentBrowserRequestCache() {
        return new HttpSessionRequestCache();
    }

    /** Resolves bearer credentials only for the authenticated POST logout route, never token or browser routes. */
    @Bean
    BearerTokenResolver coreAgentLogoutBearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        RequestMatcher logout = postPath(LOGOUT_PATH);
        return request -> logout.matches(request) ? delegate.resolve(request) : null;
    }

    /** Routes missing authentication on browser pages to login, but returns RFC 6750 errors for bearer logout. */
    @Bean
    AuthenticationEntryPoint coreAgentRouteAuthenticationEntryPoint() {
        LoginUrlAuthenticationEntryPoint browserLogin = new LoginUrlAuthenticationEntryPoint(LOGIN_PATH);
        BearerTokenAuthenticationEntryPoint bearerLogout = new BearerTokenAuthenticationEntryPoint();
        RequestMatcher logout = postPath(LOGOUT_PATH);
        return (request, response, authenticationException) -> {
            if (logout.matches(request)) {
                bearerLogout.commence(request, response, authenticationException);
                return;
            }
            browserLogin.commence(request, response, authenticationException);
        };
    }

    @Bean
    @Order(1)
    SecurityFilterChain coreAgentAuthorizationServerSecurityFilterChain(
            HttpSecurity http,
            CoreAgentBrowserLoginProperties browserLoginProperties,
            CoreAgentAuthorizationServerConfigurerFactory authorizationServerConfigurerFactory,
            CoreAgentBrowserAuthenticationProvider browserAuthenticationProvider,
            CsrfTokenRepository coreAgentBrowserCsrfTokenRepository,
            CsrfTokenRequestHandler coreAgentBrowserCsrfTokenRequestHandler,
            RequestCache coreAgentBrowserRequestCache,
            JwtDecoder jwtDecoder,
            BearerTokenResolver coreAgentLogoutBearerTokenResolver,
            AuthenticationEntryPoint coreAgentRouteAuthenticationEntryPoint) throws Exception {
        RequestMatcher browserAndTokenRoutes = exactCoreAgentRoutes();
        CsrfFilter browserAuthorizationCsrf = browserAuthorizationCsrfFilter(coreAgentBrowserCsrfTokenRepository,
                coreAgentBrowserCsrfTokenRequestHandler);

        http.securityMatcher(browserAndTokenRoutes)
                .authenticationProvider(browserAuthenticationProvider)
                .requestCache(requestCache -> requestCache.requestCache(coreAgentBrowserRequestCache))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .cors(Customizer.withDefaults())
                .csrf(csrf -> {
                    csrf.csrfTokenRepository(coreAgentBrowserCsrfTokenRepository)
                            .csrfTokenRequestHandler(coreAgentBrowserCsrfTokenRequestHandler)
                            .ignoringRequestMatchers(postPath(TOKEN_PATH), postPath(LOGOUT_PATH));
                    if (!browserLoginProperties.isCsrfEnabled()) {
                        csrf.ignoringRequestMatchers(postPath(LOGIN_PATH));
                    }
                })
                .formLogin(formLogin -> formLogin.loginPage(LOGIN_PATH)
                        .loginProcessingUrl(LOGIN_PATH)
                        .failureUrl(LOGIN_PATH + "?error")
                        .successHandler(savedRequestSuccessHandler(coreAgentBrowserRequestCache))
                        .permitAll())
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(LOGIN_PATH, TOKEN_PATH).permitAll()
                        .requestMatchers(LOGOUT_PATH).authenticated()
                        .requestMatchers(AUTHORIZE_PATH, CONSENT_PATH).authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(coreAgentRouteAuthenticationEntryPoint))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(coreAgentLogoutBearerTokenResolver)
                        .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                        .jwt(jwt -> jwt.decoder(jwtDecoder)))
                .oauth2AuthorizationServer(authorizationServerConfigurerFactory::configure)
                .addFilterBefore(browserAuthorizationCsrf, CsrfFilter.class);
        return http.build();
    }

    static RequestMatcher exactCoreAgentRoutes() {
        return new OrRequestMatcher(List.of(path(AUTHORIZE_PATH), path(TOKEN_PATH), path(LOGIN_PATH),
                path(CONSENT_PATH), path(LOGOUT_PATH)));
    }

    static CsrfFilter browserAuthorizationCsrfFilter(CsrfTokenRepository repository,
                                                     CsrfTokenRequestHandler requestHandler) {
        CsrfFilter csrfFilter = new CsrfFilter(repository);
        csrfFilter.setRequestHandler(requestHandler);
        csrfFilter.setRequireCsrfProtectionMatcher(postPath(AUTHORIZE_PATH));
        return csrfFilter;
    }

    private static SavedRequestAwareAuthenticationSuccessHandler savedRequestSuccessHandler(RequestCache requestCache) {
        SavedRequestAwareAuthenticationSuccessHandler successHandler = new SavedRequestAwareAuthenticationSuccessHandler();
        successHandler.setRequestCache(requestCache);
        return successHandler;
    }

    private static RequestMatcher path(String path) {
        return PathPatternRequestMatcher.pathPattern(path);
    }

    private static RequestMatcher postPath(String path) {
        return PathPatternRequestMatcher.pathPattern(HttpMethod.POST, path);
    }
}
