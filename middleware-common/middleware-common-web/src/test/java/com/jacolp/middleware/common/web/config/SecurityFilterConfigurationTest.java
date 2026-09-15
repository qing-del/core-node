package com.jacolp.middleware.common.web.config;

import com.jacolp.middleware.common.core.metrics.QpsCounter;
import com.jacolp.middleware.common.security.filter.LegacyJwtAuthenticationFilter;
import com.jacolp.middleware.common.security.jwt.JwtProperties;
import com.jacolp.web.config.SecurityFilterConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityFilterConfigurationTest {

    @Test
    @SuppressWarnings("unchecked")
    void filterRegistrationsAreDisabledToPreventDoubleRegistration() {
        SecurityFilterConfiguration configuration = new SecurityFilterConfiguration();
        ObjectProvider<RequestMappingHandlerMapping> mappings = mock(ObjectProvider.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        QpsCounter qps = mock(QpsCounter.class);
        JwtProperties properties = new JwtProperties();
        LegacyJwtAuthenticationFilter admin = configuration.adminJwtAuthenticationFilter(mappings, redis, properties, qps);
        LegacyJwtAuthenticationFilter user = configuration.userJwtAuthenticationFilter(mappings, redis, properties, qps);
        LegacyJwtAuthenticationFilter activation = configuration.activationJwtAuthenticationFilter(mappings, redis, properties, qps);

        assertThat(configuration.disableAdminFilterRegistration(admin).isEnabled()).isFalse();
        assertThat(configuration.disableUserFilterRegistration(user).isEnabled()).isFalse();
        assertThat(configuration.disableActivationFilterRegistration(activation).isEnabled()).isFalse();
    }

    @Test
    void realSecurityChainPreservesProtectedExcludedAndUnmappedMvcContracts() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(TestWebConfiguration.class);
            context.refresh();

            FilterChainProxy chain = context.getBean("springSecurityFilterChain", FilterChainProxy.class);
            assertThat(chain).isNotNull();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(chain).build();

            mvc.perform(get("/user/protected"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(""));
            mvc.perform(get("/user/user/login"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("login"));
            mvc.perform(get("/admin/not-mapped"))
                    .andExpect(status().isNotFound());
        }
    }

    @Configuration
    @EnableWebMvc
    @Import(SecurityFilterConfiguration.class)
    static class TestWebConfiguration {

        @Bean
        StringRedisTemplate redis() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        JwtProperties jwtProperties() {
            JwtProperties properties = new JwtProperties();
            properties.setUserSecretKey("test-user-secret");
            properties.setAdminSecretKey("test-admin-secret");
            properties.setActiveSecretKey("test-active-secret");
            return properties;
        }

        @Bean
        QpsCounter qpsCounter() {
            return mock(QpsCounter.class);
        }

        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @RestController
    static class TestController {

        @GetMapping("/user/protected")
        String protectedEndpoint() {
            return "protected";
        }

        @GetMapping("/user/user/login")
        String login() {
            return "login";
        }
    }
}
