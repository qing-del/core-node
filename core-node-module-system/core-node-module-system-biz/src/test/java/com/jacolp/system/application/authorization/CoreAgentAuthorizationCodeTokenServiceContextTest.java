package com.jacolp.system.application.authorization;

import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.common.security.oauth2.token.OAuth2RefreshTokenSessionService;
import com.jacolp.common.security.oauth2.token.Rs256AccessTokenIssuer;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CoreAgentAuthorizationCodeTokenServiceContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ServiceOnlyConfiguration.class);

    @Test
    void createsExactlyOneTokenService() {
        runner.withUserConfiguration(DependencyConfiguration.class)
                .run(context -> assertThat(context.getBeansOfType(CoreAgentAuthorizationCodeTokenService.class)).hasSize(1));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentAuthorizationCodeTokenService.class)
    static class ServiceOnlyConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class DependencyConfiguration {
        @Bean CoreAgentRegisteredClientPolicyResolver policyResolver() { return Mockito.mock(CoreAgentRegisteredClientPolicyResolver.class); }
        @Bean
        AuthorizationAccountRepository accountRepository() { return mock(AuthorizationAccountRepository.class); }
        @Bean
        AccountGrantTypeResolver accountGrantTypeResolver() { return new AccountGrantTypeResolver(AccountGrantTypeResolver.requiredDefaultGrantTypes()); }
        @Bean EffectiveRolePermissionResolver rolePermissionResolver() { return Mockito.mock(EffectiveRolePermissionResolver.class); }
        @Bean OAuth2ScopeResolver scopeResolver() { return Mockito.mock(OAuth2ScopeResolver.class); }
        @Bean
        Rs256AccessTokenIssuer accessTokenIssuer() { return mock(Rs256AccessTokenIssuer.class); }
        @Bean
        OAuth2RefreshTokenSessionService refreshTokenSessionService() { return mock(OAuth2RefreshTokenSessionService.class); }
    }
}
