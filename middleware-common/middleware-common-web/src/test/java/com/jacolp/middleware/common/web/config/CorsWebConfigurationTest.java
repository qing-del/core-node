package com.jacolp.middleware.common.web.config;

import com.jacolp.common.web.config.CorsProperties;
import com.jacolp.common.web.config.CorsWebConfiguration;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorsWebConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CorsWebConfiguration.class);

    @Test
    void bindsTheConfiguredCorsPolicyForTheSecuritySource() {
        contextRunner.withPropertyValues(
                        "jacolp.web.cors.allowed-origin-patterns=https://app.example,https://admin.example",
                        "jacolp.web.cors.allowed-methods=GET,POST,OPTIONS",
                        "jacolp.web.cors.allowed-headers=Authorization,Content-Type",
                        "jacolp.web.cors.exposed-headers=X-Request-Id",
                        "jacolp.web.cors.allow-credentials=true",
                        "jacolp.web.cors.allow-private-network=true",
                        "jacolp.web.cors.max-age=1h")
                .run(context -> {
                    CorsProperties properties = context.getBean(CorsProperties.class);
                    assertThat(properties.getAllowedOriginPatterns())
                            .containsExactly("https://app.example", "https://admin.example");
                    assertThat(properties.getAllowedMethods()).containsExactly("GET", "POST", "OPTIONS");
                    assertThat(properties.getAllowedHeaders()).containsExactly("Authorization", "Content-Type");
                    assertThat(properties.getExposedHeaders()).containsExactly("X-Request-Id");
                    assertThat(properties.isAllowCredentials()).isTrue();
                    assertThat(properties.isAllowPrivateNetwork()).isTrue();
                    assertThat(properties.getMaxAge()).isEqualTo(Duration.ofHours(1));

                    CorsConfiguration configuration = context.getBean(CorsConfigurationSource.class)
                            .getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest());
                    assertThat(configuration).isNotNull();
                    assertThat(configuration.getAllowedOriginPatterns())
                            .containsExactly("https://app.example", "https://admin.example");
                    assertThat(configuration.getAllowedMethods()).containsExactly("GET", "POST", "OPTIONS");
                    assertThat(configuration.getAllowedHeaders()).containsExactly("Authorization", "Content-Type");
                    assertThat(configuration.getExposedHeaders()).containsExactly("X-Request-Id");
                    assertThat(configuration.getAllowCredentials()).isTrue();
                    assertThat(configuration.getAllowPrivateNetwork()).isTrue();
                    assertThat(configuration.getMaxAge()).isEqualTo(Duration.ofHours(1).toSeconds());
                });
    }

    @Test
    void appliesTheConfiguredPolicyToAControllerWithoutCrossOriginAnnotation() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            ServletContext servletContext = new MockServletContext();
            context.setServletContext(servletContext);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("cors-test", Map.of(
                    "jacolp.web.cors.allowed-origin-patterns", "https://app.example",
                    "jacolp.web.cors.allowed-methods", "GET,OPTIONS",
                    "jacolp.web.cors.allowed-headers", "Authorization,Content-Type",
                    "jacolp.web.cors.max-age", "10m")));
            context.register(TestWebConfiguration.class);
            context.refresh();

            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
            mvc.perform(options("/cors-test")
                            .header("Origin", "https://app.example")
                            .header("Access-Control-Request-Method", "GET")
                            .header("Access-Control-Request-Headers", "Authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example"))
                    .andExpect(header().string("Access-Control-Allow-Methods", "GET,OPTIONS"))
                    .andExpect(header().string("Access-Control-Allow-Headers", "Authorization"));

            mvc.perform(options("/cors-test")
                            .header("Origin", "https://evil.example")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isForbidden());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(CorsWebConfiguration.class)
    static class TestWebConfiguration {

        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @RestController
    static class TestController {

        @GetMapping("/cors-test")
        String get() {
            return "ok";
        }
    }
}
