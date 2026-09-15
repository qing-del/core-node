package com.jacolp.middleware.config;

import com.jacolp.common.web.config.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CorsPropertiesTest {

    @Test
    void applicationYamlDeclaresTheSharedCorsPolicy() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (PropertySource<?> source : loader.load("application.yaml", new ClassPathResource("application.yaml"))) {
            environment.getPropertySources().addFirst(source);
        }

        CorsProperties properties = Binder.get(environment)
                .bind("jacolp.web.cors", Bindable.of(CorsProperties.class))
                .orElseThrow(() -> new IllegalStateException("CORS properties did not bind"));

        assertThat(properties.getAllowedOriginPatterns()).containsExactly("*");
        assertThat(properties.getAllowedMethods())
                .containsExactly("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(properties.getAllowedHeaders()).containsExactly("*");
        assertThat(properties.getExposedHeaders()).isEmpty();
        assertThat(properties.isAllowCredentials()).isFalse();
        assertThat(properties.isAllowPrivateNetwork()).isFalse();
        assertThat(properties.getMaxAge()).isEqualTo(Duration.ofMinutes(30));
    }
}
