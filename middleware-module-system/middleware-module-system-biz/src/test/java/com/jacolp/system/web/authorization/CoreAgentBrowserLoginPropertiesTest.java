package com.jacolp.system.web.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class CoreAgentBrowserLoginPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void defaultsToDisabled() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CoreAgentBrowserLoginProperties.class).isCsrfEnabled()).isFalse();
        });
    }

    @Test
    void bindsExplicitlyEnabledValue() {
        runner.withPropertyValues("jacolp.oauth2.browser-login.csrf-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CoreAgentBrowserLoginProperties.class).isCsrfEnabled()).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CoreAgentBrowserLoginProperties.class)
    static class PropertiesConfiguration {
    }
}
