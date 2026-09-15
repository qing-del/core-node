package com.jacolp.middleware.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCookiePropertiesTest {

    @Test
    void baseSessionCookiePolicyBindsForLocalHttp() throws IOException {
        ServerProperties properties = bind("application.yaml");

        assertThat(properties.getServlet().getSession().getTimeout()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.getServlet().getSession().getCookie().getHttpOnly()).isTrue();
        assertThat(properties.getServlet().getSession().getCookie().getSameSite()).isEqualTo(Cookie.SameSite.LAX);
        assertThat(properties.getServlet().getSession().getCookie().getSecure()).isFalse();
    }

    @Test
    void productionProfileDefaultsTheCookieToSecureWhileKeepingTheSharedPolicy() throws IOException {
        ServerProperties properties = bind("application.yaml", "application-prod.yaml");

        assertThat(properties.getServlet().getSession().getTimeout()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.getServlet().getSession().getCookie().getHttpOnly()).isTrue();
        assertThat(properties.getServlet().getSession().getCookie().getSameSite()).isEqualTo(Cookie.SameSite.LAX);
        assertThat(properties.getServlet().getSession().getCookie().getSecure()).isTrue();
    }

    private static ServerProperties bind(String... resources) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String resource : resources) {
            for (PropertySource<?> source : loader.load(resource, new ClassPathResource(resource))) {
                environment.getPropertySources().addFirst(source);
            }
        }
        return Binder.get(environment).bind("server", Bindable.of(ServerProperties.class))
                .orElseThrow(() -> new IllegalStateException("server properties did not bind"));
    }
}
