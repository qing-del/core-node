package com.jacolp.system.infrastructure.authorization;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/** Configures the official SAS JDBC consent service against the existing oauth2_authorization_consent table. */
@Configuration(proxyBeanMethods = false)
public class CoreAgentAuthorizationConsentConfiguration {

    @Bean
    OAuth2AuthorizationConsentService oauth2AuthorizationConsentService(JdbcOperations jdbcOperations,
                                                                          RegisteredClientRepository registeredClients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcOperations, registeredClients);
    }
}
