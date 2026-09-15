package com.jacolp.system.application.authorization;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CoreAgentBrowserAuthenticationProviderContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProviderOnlyConfiguration.class);

    @Test
    void createsOneProvider() {
        runner.withUserConfiguration(DependencyConfiguration.class)
                .run(context -> assertThat(context.getBeansOfType(CoreAgentBrowserAuthenticationProvider.class)).hasSize(1));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentBrowserAuthenticationProvider.class)
    static class ProviderOnlyConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class DependencyConfiguration {
        @Bean
        CoreAgentBrowserAccountAuthenticator accountAuthenticator() {
            return Mockito.mock(CoreAgentBrowserAccountAuthenticator.class);
        }
    }
}
