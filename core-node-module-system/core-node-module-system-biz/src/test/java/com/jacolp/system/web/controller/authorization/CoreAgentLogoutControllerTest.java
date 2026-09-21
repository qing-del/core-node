package com.jacolp.system.web.controller.authorization;

import com.jacolp.common.core.result.Result;
import com.jacolp.system.application.authorization.CoreAgentLogoutService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.PostMapping;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CoreAgentLogoutControllerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void declaresExactPostLogoutWithoutInputAndReturnsSuccess() throws Exception {
        CoreAgentLogoutService service = mock(CoreAgentLogoutService.class);
        CoreAgentLogoutController controller = new CoreAgentLogoutController(service);
        PostMapping mapping = CoreAgentLogoutController.class.getMethod("logout").getAnnotation(PostMapping.class);

        Result<Void> result = controller.logout();

        assertThat(mapping.value()).containsExactly("/oauth/logout");
        assertThat(CoreAgentLogoutController.class.getMethod("logout").getParameterCount()).isZero();
        assertThat(result.getCode()).isEqualTo(Result.SUCCESS);
        verify(service).logout();
    }

    @Test
    void serviceFailurePropagatesWithoutTranslatingAuthenticationState() {
        CoreAgentLogoutService service = mock(CoreAgentLogoutService.class);
        IllegalStateException failure = new IllegalStateException("redis");
        doThrow(failure).when(service).logout();

        assertThatThrownBy(() -> new CoreAgentLogoutController(service).logout()).isSameAs(failure);
    }

    @Test
    void contextsAreBoundedAndSourceNeverReadsBearerMaterial() throws Exception {
        runner.withUserConfiguration(Dependencies.class)
                .run(context -> assertThat(context.getBeansOfType(CoreAgentLogoutController.class)).hasSize(1));

        String source = Files.readString(Path.of("src/main/java/com/jacolp/module/system/biz/web/controller/authorization/"
                + "CoreAgentLogoutController.java"));
        assertThat(source).doesNotContain("RequestBody", "RequestParam", "getHeader", "getRemoteAddr", "Logger", "log.",
                "tokenValue", "Authorization");
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentLogoutController.class)
    static class Config {
    }

    @Configuration(proxyBeanMethods = false)
    static class Dependencies {
        @Bean CoreAgentLogoutService coreAgentLogoutService() { return mock(CoreAgentLogoutService.class); }
    }
}
