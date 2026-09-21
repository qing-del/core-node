package com.jacolp.system.web.controller.authorization;

import com.jacolp.common.core.result.Result;
import com.jacolp.system.application.authorization.InternalLogoutService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InternalLogoutControllerTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void declaresExactPostLogoutWithoutRequestBodyAndReturnsSuccess() throws Exception {
        InternalLogoutService service = mock(InternalLogoutService.class);
        InternalLogoutController controller = new InternalLogoutController(service);
        RequestMapping mapping = InternalLogoutController.class.getAnnotation(RequestMapping.class);
        PostMapping post = InternalLogoutController.class.getMethod("logout").getAnnotation(PostMapping.class);

        Result<Void> result = controller.logout();

        assertThat(mapping.value()).containsExactly("/auth");
        assertThat(post.value()).containsExactly("/logout");
        assertThat(InternalLogoutController.class.getMethod("logout").getParameterCount()).isZero();
        assertThat(result.getCode()).isEqualTo(Result.SUCCESS);
        verify(service).logout();
    }

    @Test
    void serviceExceptionPropagates() {
        InternalLogoutService service = mock(InternalLogoutService.class);
        IllegalStateException failure = new IllegalStateException("redis");
        doThrow(failure).when(service).logout();
        assertThatThrownBy(() -> new InternalLogoutController(service).logout()).isSameAs(failure);
    }

    @Test
    void contextsAreBoundedAndSourceAvoidsSensitiveApis() throws Exception {
        runner.withUserConfiguration(Dependencies.class)
                .run(context -> assertThat(context.getBeansOfType(InternalLogoutController.class)).hasSize(1));
        String source = Files.readString(Path.of("src/main/java/com/jacolp/module/system/biz/web/controller/authorization/InternalLogoutController.java"));
        assertThat(source).doesNotContain("RequestBody", "getHeader", "getRemoteAddr", "Logger", "log.", "tokenValue");
    }

    @Configuration(proxyBeanMethods = false)
    @Import(InternalLogoutController.class)
    static class Config { }

    @Configuration(proxyBeanMethods = false)
    static class Dependencies {
        @Bean InternalLogoutService logoutService() { return mock(InternalLogoutService.class); }
    }
}
