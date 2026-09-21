package com.jacolp.system.application.authorization;

import com.jacolp.common.security.context.CurrentAccessTokenAccessor;
import com.jacolp.common.security.oauth2.token.OAuth2SessionRevocationStore;
import com.jacolp.common.security.oauth2.token.OAuth2TokenStateStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InternalLogoutServiceContextTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void createsOneBeanWithThreeDependencies() {
        runner.withUserConfiguration(Dependencies.class)
                .run(context -> assertThat(context.getBeansOfType(InternalLogoutService.class)).hasSize(1));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(InternalLogoutService.class)
    static class Config { }

    @Configuration(proxyBeanMethods = false)
    static class Dependencies {
        @Bean
        CurrentAccessTokenAccessor access() { return mock(CurrentAccessTokenAccessor.class); }
        @Bean
        OAuth2TokenStateStore states() { return mock(OAuth2TokenStateStore.class); }
        @Bean
        OAuth2SessionRevocationStore revocations() { return mock(OAuth2SessionRevocationStore.class); }
    }
}
