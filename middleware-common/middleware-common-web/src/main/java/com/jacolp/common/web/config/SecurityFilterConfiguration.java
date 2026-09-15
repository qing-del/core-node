package com.jacolp.common.web.config;

import com.jacolp.common.security.filter.ActivationJwtAuthenticationFilter;
import com.jacolp.common.security.jwt.JwtProperties;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
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
    public ActivationJwtAuthenticationFilter activationJwtAuthenticationFilter(
            ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider, JwtProperties jwtProperties) {
        return new ActivationJwtAuthenticationFilter(handlerMappingProvider, jwtProperties);
    }

    @Bean
    @Order(3)
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ActivationJwtAuthenticationFilter activationJwtAuthenticationFilter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .addFilterBefore(activationJwtAuthenticationFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public FilterRegistrationBean<Filter> disableActivationFilterRegistration(ActivationJwtAuthenticationFilter activationJwtAuthenticationFilter) {
        return disabledRegistration(activationJwtAuthenticationFilter);
    }

    private static FilterRegistrationBean<Filter> disabledRegistration(Filter filter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
