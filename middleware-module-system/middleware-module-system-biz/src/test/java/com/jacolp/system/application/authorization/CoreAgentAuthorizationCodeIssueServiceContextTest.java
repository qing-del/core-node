package com.jacolp.system.application.authorization;

import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.common.security.oauth2.token.SecureOAuth2TokenGenerator;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.system.application.port.out.CoreAgentPendingAuthorizationCodeTransitionStore;
import com.jacolp.system.application.port.out.CoreAgentPendingAuthorizationStore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CoreAgentAuthorizationCodeIssueServiceContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ServiceOnlyConfiguration.class);

    @Test
    void createsOneServiceWithAllRuntimeDependenciesWithoutClockBean() {
        runner.withUserConfiguration(DependencyConfiguration.class)
                .run(context -> assertThat(context.getBeansOfType(CoreAgentAuthorizationCodeIssueService.class)).hasSize(1));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentAuthorizationCodeIssueService.class)
    static class ServiceOnlyConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class DependencyConfiguration {
        @Bean
        SecureOAuth2TokenGenerator tokenGenerator() { return new SecureOAuth2TokenGenerator(); }
        @Bean CoreAgentRegisteredClientPolicyResolver policyResolver() { return Mockito.mock(CoreAgentRegisteredClientPolicyResolver.class); }
        @Bean
        AuthorizationAccountRepository accountRepository() { return mock(AuthorizationAccountRepository.class); }
        @Bean
        AccountGrantTypeResolver accountGrantTypeResolver() {
            return new AccountGrantTypeResolver(AccountGrantTypeResolver.requiredDefaultGrantTypes());
        }
        @Bean EffectiveRolePermissionResolver rolePermissionResolver() { return Mockito.mock(EffectiveRolePermissionResolver.class); }
        @Bean CoreAgentConsentScopeService consentScopeService() {
            return new CoreAgentConsentScopeService(new OAuth2ScopeResolver());
        }
        @Bean CoreAgentPendingAuthorizationHandleGenerator pendingHandleGenerator(SecureOAuth2TokenGenerator generator) {
            return new CoreAgentPendingAuthorizationHandleGenerator(generator);
        }
        @Bean
        CoreAgentPendingAuthorizationStore pendingAuthorizationStore() {
            return mock(CoreAgentPendingAuthorizationStore.class);
        }
        @Bean
        CoreAgentPendingAuthorizationCodeTransitionStore transitionStore() {
            return mock(CoreAgentPendingAuthorizationCodeTransitionStore.class);
        }
    }
}
