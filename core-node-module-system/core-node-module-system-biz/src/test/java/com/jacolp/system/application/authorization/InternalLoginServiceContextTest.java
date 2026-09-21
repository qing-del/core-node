package com.jacolp.system.application.authorization;

import com.jacolp.common.security.oauth2.token.OAuth2RefreshTokenSessionService;
import com.jacolp.common.security.oauth2.token.Rs256AccessTokenIssuer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InternalLoginServiceContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ServiceOnlyConfiguration.class);

    @Test
    void createsExactlyOneServiceWithEightDependencies() {
        runner.withUserConfiguration(DependencyConfiguration.class)
                .run(context -> {
                    assertThat(context.getBeansOfType(InternalLoginService.class)).hasSize(1);
                    assertThat(context.getBeansOfType(InternalRegisteredClientPolicyResolver.class)).hasSize(1);
                    assertThat(context.getBeansOfType(InternalPasswordAccountAuthenticator.class)).hasSize(1);
                    assertThat(context.getBeansOfType(EmailLoginCodeAuthenticator.class)).hasSize(1);
                    assertThat(context.getBeansOfType(EffectiveRolePermissionResolver.class)).hasSize(1);
                    assertThat(context.getBeansOfType(OAuth2ScopeResolver.class)).hasSize(1);
                    Assertions.assertThat(context.getBeansOfType(Rs256AccessTokenIssuer.class)).hasSize(1);
                    Assertions.assertThat(context.getBeansOfType(OAuth2RefreshTokenSessionService.class)).hasSize(1);
                    assertThat(context.getBeansOfType(InternalRefreshTokenService.class)).hasSize(1);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(InternalLoginService.class)
    static class ServiceOnlyConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class DependencyConfiguration {
        @Bean
        InternalRegisteredClientPolicyResolver policyResolver() {
            return Mockito.mock(InternalRegisteredClientPolicyResolver.class);
        }

        @Bean
        InternalPasswordAccountAuthenticator passwordAuthenticator() {
            return Mockito.mock(InternalPasswordAccountAuthenticator.class);
        }

        @Bean
        EmailLoginCodeAuthenticator emailCodeAuthenticator() {
            return Mockito.mock(EmailLoginCodeAuthenticator.class);
        }

        @Bean
        EffectiveRolePermissionResolver rolePermissionResolver() {
            return Mockito.mock(EffectiveRolePermissionResolver.class);
        }

        @Bean
        OAuth2ScopeResolver scopeResolver() {
            return Mockito.mock(OAuth2ScopeResolver.class);
        }

        @Bean
        Rs256AccessTokenIssuer accessTokenIssuer() {
            return mock(Rs256AccessTokenIssuer.class);
        }

        @Bean
        OAuth2RefreshTokenSessionService refreshTokenSessionService() {
            return mock(OAuth2RefreshTokenSessionService.class);
        }

        @Bean
        InternalRefreshTokenService internalRefreshTokenService() {
            return Mockito.mock(InternalRefreshTokenService.class);
        }
    }
}
