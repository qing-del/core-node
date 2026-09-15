package com.jacolp.security.jwt;

import com.jacolp.common.security.jwt.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertiesConfiguration.class));

    @Test
    void bindsOnlyTheActivationCredentialConfiguration() {
        contextRunner.withPropertyValues(
                        "jacolp.jwt.active-secret-key=activation-secret",
                        "jacolp.jwt.active-ttl=900000",
                        "jacolp.jwt.active-code-ttl=300000",
                        "jacolp.jwt.active-token-name=activeToken")
                .run(context -> {
                    JwtProperties properties = context.getBean(JwtProperties.class);

                    assertThat(properties.getActiveSecretKey()).isEqualTo("activation-secret");
                    assertThat(properties.getActiveTtl()).isEqualTo(900000L);
                    assertThat(properties.getActiveCodeTtl()).isEqualTo(300000L);
                    assertThat(properties.getActiveTokenName()).isEqualTo("activeToken");
                    assertThat(Arrays.stream(JwtProperties.class.getDeclaredFields()).map(Field::getName))
                            .noneMatch(name -> name.startsWith("admin") || name.startsWith("user"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class PropertiesConfiguration {
    }
}
