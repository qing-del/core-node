package com.jacolp.system.application.authorization;

import com.jacolp.system.application.port.out.CoreAgentAuthorizationCodeStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AccountAuthorizationStateRevocationServiceContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void redisBackedImplementationIsAlwaysTheOnlyImplementation() {
        runner.withUserConfiguration(Dependencies.class)
                .run(context -> assertSingleImplementation(context,
                        CoreAgentAccountAuthorizationStateRevocationService.class));
    }

    private static void assertSingleImplementation(org.springframework.boot.test.context.assertj.AssertableApplicationContext context,
                                                   Class<? extends AccountAuthorizationStateRevocationService> type) {
        var services = context.getBeansOfType(AccountAuthorizationStateRevocationService.class);
        assertThat(services).hasSize(1);
        assertThat(services.values().iterator().next()).isInstanceOf(type);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentAccountAuthorizationStateRevocationService.class)
    static class Config {
    }

    @Configuration(proxyBeanMethods = false)
    static class Dependencies {
        @Bean
        CoreAgentAuthorizationCodeStore authorizationCodeStore() { return mock(CoreAgentAuthorizationCodeStore.class); }
    }
}
