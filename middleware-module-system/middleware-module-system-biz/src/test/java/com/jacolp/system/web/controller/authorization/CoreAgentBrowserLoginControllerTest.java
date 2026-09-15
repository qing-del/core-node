package com.jacolp.system.web.controller.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class CoreAgentBrowserLoginControllerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ControllerOnlyConfiguration.class);

    @Test
    void mapsOnlyTheExactOauthLoginGetPath() throws Exception {
        GetMapping getMapping = CoreAgentBrowserLoginController.class
                .getMethod("login", String.class, org.springframework.ui.Model.class)
                .getAnnotation(GetMapping.class);

        assertThat(getMapping.value()).containsExactly("/oauth/login");
        assertThat(CoreAgentBrowserLoginController.class.getMethod("login", String.class, org.springframework.ui.Model.class)
                .getAnnotation(PostMapping.class)).isNull();

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CoreAgentBrowserLoginController()).build();
        mvc.perform(get("/oauth/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("oauth/login"));
        mvc.perform(post("/oauth/login"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void errorParameterProducesOnlyTheFixedGenericMessage() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CoreAgentBrowserLoginController()).build();

        mvc.perform(get("/oauth/login").param("error", "username=alice&password=secret"))
                .andExpect(status().isOk())
                .andExpect(view().name("oauth/login"))
                .andExpect(model().attribute("oauthLoginError",
                        CoreAgentBrowserLoginController.GENERIC_LOGIN_ERROR_MESSAGE));
    }

    @Test
    void registersController() {
        runner.run(context -> assertThat(context.getBeansOfType(CoreAgentBrowserLoginController.class)).hasSize(1));
    }

    @Test
    void templateHasOnlyTheBrowserCredentialAndCsrfFormSurface() throws IOException {
        String template = readTemplate();
        String lowerCaseTemplate = template.toLowerCase(java.util.Locale.ROOT);

        assertThat(template).contains("<form method=\"post\" action=\"/oauth/login\"",
                "th:action=\"@{/oauth/login}\"", "${_csrf", "name=\"username\"", "name=\"password\"",
                "autocomplete=\"username\"", "autocomplete=\"current-password\"",
                "登录失败，请检查账号或密码后重试。");
        assertThat(template).doesNotContain("${param", "param.", "request.");
        assertThat(lowerCaseTemplate).doesNotContain("client_secret", "email-code", "remember-me", "x-forwarded-for",
                "<script", "src=\"http", "href=\"http", "logger", "log.");
    }

    private static String readTemplate() throws IOException {
        try (InputStream stream = CoreAgentBrowserLoginControllerTest.class.getClassLoader()
                .getResourceAsStream("templates/oauth/login.html")) {
            assertThat(stream).as("login template classpath resource").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentBrowserLoginController.class)
    static class ControllerOnlyConfiguration {
    }
}
