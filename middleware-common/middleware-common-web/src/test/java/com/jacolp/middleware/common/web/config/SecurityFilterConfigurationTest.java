package com.jacolp.middleware.common.web.config;

import com.jacolp.common.security.filter.ActivationJwtAuthenticationFilter;
import com.jacolp.common.security.jwt.JwtProperties;
import com.jacolp.common.web.config.SecurityFilterConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityFilterConfigurationTest {

    @Test
    void fallbackSecurityChainHasLowerOrderThanTheFutureAuthorizationServerChain() throws NoSuchMethodException {
        Method chainBean = SecurityFilterConfiguration.class.getDeclaredMethod("securityFilterChain", HttpSecurity.class,
                ActivationJwtAuthenticationFilter.class);

        assertThat(chainBean.getAnnotation(Order.class)).isNotNull();
        assertThat(chainBean.getAnnotation(Order.class).value()).isEqualTo(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void filterRegistrationsAreDisabledToPreventDoubleRegistration() {
        SecurityFilterConfiguration configuration = new SecurityFilterConfiguration();
        ObjectProvider<RequestMappingHandlerMapping> mappings = mock(ObjectProvider.class);
        JwtProperties properties = new JwtProperties();
        ActivationJwtAuthenticationFilter activation = configuration.activationJwtAuthenticationFilter(mappings, properties);

        assertThat(configuration.disableActivationFilterRegistration(activation).isEnabled()).isFalse();
    }

    @Test
    void fallbackSecurityChainOnlyRetainsTheActivationCredentialException() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(TestWebConfiguration.class);
            context.refresh();

            FilterChainProxy chain = context.getBean("springSecurityFilterChain", FilterChainProxy.class);
            assertThat(chain).isNotNull();
            assertThat(chain.getFilterChains()).hasSize(1);
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(chain).build();

            mvc.perform(get("/user/protected"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("protected"));
            mvc.perform(get("/admin/not-mapped"))
                    .andExpect(status().isNotFound());
        }
    }

    @Configuration
    @EnableWebMvc
    @Import(SecurityFilterConfiguration.class)
    static class TestWebConfiguration {

        @Bean
        JwtProperties jwtProperties() {
            JwtProperties properties = new JwtProperties();
            properties.setActiveSecretKey("test-active-secret");
            return properties;
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

    }
}
