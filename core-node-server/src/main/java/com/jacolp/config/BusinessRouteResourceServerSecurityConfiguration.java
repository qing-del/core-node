package com.jacolp.config;

import com.jacolp.common.security.oauth2.authorization.BusinessRouteAuthorizationManager;
import com.jacolp.common.security.oauth2.authorization.BusinessRouteAuthorizationPolicy;
import com.jacolp.common.security.oauth2.authorization.CoreNodeJsonAccessDeniedHandler;
import com.jacolp.common.security.oauth2.authorization.CoreNodeJsonAuthenticationEntryPoint;
import com.jacolp.common.security.oauth2.authorization.CoreNodeJwtAuthenticationConverter;
import com.jacolp.common.security.oauth2.authorization.InternalLogoutAuthorizationManager;
import com.jacolp.common.security.oauth2.jwt.CoreNodeAccessTokenClaimsValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Stateless RS256 resource-server chain for the executable Phase 5 business route catalogue.
 * The matcher is intentionally limited to catalogue entries so the four public activation exceptions fall through.
 */
@Configuration(proxyBeanMethods = false)
public class BusinessRouteResourceServerSecurityConfiguration {

    @Bean
    @Order(2)
    SecurityFilterChain businessRouteResourceServerSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("businessResourceServerRequestMatcher") RequestMatcher businessResourceServerRequestMatcher,
            @Qualifier("internalLogoutRequestMatcher") RequestMatcher internalLogoutRequestMatcher,
            BusinessRouteAuthorizationPolicy businessRouteAuthorizationPolicy,
            JwtDecoder jwtDecoder,
            CoreNodeAccessTokenClaimsValidator coreNodeAccessTokenClaimsValidator) throws Exception {
        CoreNodeJsonAuthenticationEntryPoint authenticationEntryPoint = new CoreNodeJsonAuthenticationEntryPoint();
        CoreNodeJsonAccessDeniedHandler accessDeniedHandler = new CoreNodeJsonAccessDeniedHandler();
        BusinessRouteAuthorizationManager authorizationManager =
                new BusinessRouteAuthorizationManager(businessRouteAuthorizationPolicy);
        CoreNodeJwtAuthenticationConverter authenticationConverter =
                new CoreNodeJwtAuthenticationConverter(coreNodeAccessTokenClaimsValidator);

        http.securityMatcher(businessResourceServerRequestMatcher)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(internalLogoutRequestMatcher).access(new InternalLogoutAuthorizationManager())
                        .anyRequest().access(authorizationManager))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .authenticationConverter(new BearerTokenAuthenticationConverter())
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(authenticationConverter)));
        return http.build();
    }
}
