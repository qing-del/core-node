package com.jacolp.web.config;

import com.jacolp.middleware.common.core.metrics.QpsCounter;
import com.jacolp.middleware.common.security.context.SecurityIdentity;
import com.jacolp.middleware.common.security.filter.LegacyJwtAuthenticationFilter;
import com.jacolp.middleware.common.security.jwt.JwtProperties;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
@EnableWebSecurity
public class SecurityFilterConfiguration {

    @Bean
    public LegacyJwtAuthenticationFilter adminJwtAuthenticationFilter(
            ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider, StringRedisTemplate redis,
            JwtProperties jwtProperties, QpsCounter qpsCounter) {
        return new LegacyJwtAuthenticationFilter(SecurityIdentity.ADMIN, handlerMappingProvider, redis, jwtProperties, qpsCounter);
    }

    @Bean
    public LegacyJwtAuthenticationFilter userJwtAuthenticationFilter(
            ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider, StringRedisTemplate redis,
            JwtProperties jwtProperties, QpsCounter qpsCounter) {
        return new LegacyJwtAuthenticationFilter(SecurityIdentity.USER, handlerMappingProvider, redis, jwtProperties, qpsCounter);
    }

    @Bean
    public LegacyJwtAuthenticationFilter activationJwtAuthenticationFilter(
            ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider, StringRedisTemplate redis,
            JwtProperties jwtProperties, QpsCounter qpsCounter) {
        return new LegacyJwtAuthenticationFilter(SecurityIdentity.ACTIVATION, handlerMappingProvider, redis, jwtProperties, qpsCounter);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            LegacyJwtAuthenticationFilter adminJwtAuthenticationFilter,
                                            LegacyJwtAuthenticationFilter userJwtAuthenticationFilter,
                                            LegacyJwtAuthenticationFilter activationJwtAuthenticationFilter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .addFilterBefore(adminJwtAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .addFilterBefore(userJwtAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .addFilterBefore(activationJwtAuthenticationFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public FilterRegistrationBean<Filter> disableAdminFilterRegistration(LegacyJwtAuthenticationFilter adminJwtAuthenticationFilter) {
        return disabledRegistration(adminJwtAuthenticationFilter);
    }

    @Bean
    public FilterRegistrationBean<Filter> disableUserFilterRegistration(LegacyJwtAuthenticationFilter userJwtAuthenticationFilter) {
        return disabledRegistration(userJwtAuthenticationFilter);
    }

    @Bean
    public FilterRegistrationBean<Filter> disableActivationFilterRegistration(LegacyJwtAuthenticationFilter activationJwtAuthenticationFilter) {
        return disabledRegistration(activationJwtAuthenticationFilter);
    }

    private static FilterRegistrationBean<Filter> disabledRegistration(Filter filter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
