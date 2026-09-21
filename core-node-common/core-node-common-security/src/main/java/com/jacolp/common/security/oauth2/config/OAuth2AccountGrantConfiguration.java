package com.jacolp.common.security.oauth2.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Independently assembled account-grant resolution infrastructure.
 */
@Configuration(proxyBeanMethods = false)
public class OAuth2AccountGrantConfiguration {

    @Bean
    @ConditionalOnMissingBean(AccountGrantTypeResolver.class)
    AccountGrantTypeResolver accountGrantTypeResolver(OAuth2AccountGrantProperties properties) {
        return new AccountGrantTypeResolver(properties.getDefaultGrantTypes());
    }
}
