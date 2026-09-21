package com.jacolp.security.oauth2.config;

import com.jacolp.common.security.oauth2.config.OAuth2Rs256Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2Rs256PropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertiesConfiguration.class));

    @Test
    void defaultsKeepIssuerAudienceAndKeyLocationsUnconfigured() {
        contextRunner.run(context -> {
            OAuth2Rs256Properties properties = context.getBean(OAuth2Rs256Properties.class);

            assertThat(properties.getIssuer()).isEqualTo("core-node");
            assertThat(properties.getAudience()).isEqualTo("core-node-api");
            assertThat(properties.getPrivateKeyLocation()).isNull();
            assertThat(properties.getPublicKeyLocation()).isNull();
            assertThat(Arrays.stream(OAuth2Rs256Properties.class.getDeclaredFields())
                    .map(Field::getName)
                    .noneMatch(name -> name.toLowerCase().contains("ttl")))
                    .isTrue();
        });
    }

    @Test
    void bindsExternalPemResources(@TempDir Path temporaryDirectory) throws Exception {
        Path privateKey = Files.createFile(temporaryDirectory.resolve("private.pem"));
        Path publicKey = Files.createFile(temporaryDirectory.resolve("public.pem"));

        contextRunner.withPropertyValues(
                "jacolp.oauth2.rs256.issuer=test-issuer",
                "jacolp.oauth2.rs256.audience=test-audience",
                "jacolp.oauth2.rs256.private-key-location=" + privateKey.toUri(),
                "jacolp.oauth2.rs256.public-key-location=" + publicKey.toUri())
                .run(context -> {
                    OAuth2Rs256Properties properties = context.getBean(OAuth2Rs256Properties.class);

                    assertThat(properties.getIssuer()).isEqualTo("test-issuer");
                    assertThat(properties.getAudience()).isEqualTo("test-audience");
                    assertThat(properties.getPrivateKeyLocation().exists()).isTrue();
                    assertThat(properties.getPublicKeyLocation().exists()).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OAuth2Rs256Properties.class)
    static class PropertiesConfiguration {
    }
}
