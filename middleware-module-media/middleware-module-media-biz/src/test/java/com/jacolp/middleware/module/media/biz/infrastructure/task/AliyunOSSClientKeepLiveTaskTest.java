package com.jacolp.middleware.module.media.biz.infrastructure.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.jacolp.framework.oss.AliyunOSSClient;
import com.jacolp.media.infrastructure.task.AliyunOSSClientKeepLiveTask;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class AliyunOSSClientKeepLiveTaskTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AliyunOSSClient.class, () -> mock(AliyunOSSClient.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void doesNotCreateKeepLiveTaskWhenOnlyOssClientIsEnabled() {
        contextRunner
                .withPropertyValues("jacolp.aliyun.oss.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(AliyunOSSClientKeepLiveTask.class));
    }

    @Test
    void createsKeepLiveTaskOnlyWhenBothSwitchesAreEnabled() {
        contextRunner
                .withPropertyValues(
                        "jacolp.aliyun.oss.enabled=true",
                        "jacolp.aliyun.oss.keep-live-enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(AliyunOSSClientKeepLiveTask.class));
    }

    @Test
    void globalOssSwitchStillDisablesKeepLiveTask() {
        contextRunner
                .withPropertyValues(
                        "jacolp.aliyun.oss.enabled=false",
                        "jacolp.aliyun.oss.keep-live-enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(AliyunOSSClientKeepLiveTask.class));
    }

    @Test
    void applicationAndComposeDefaultKeepLiveToDisabled() throws Exception {
        Path root = locateRepositoryRoot();
        String application = Files.readString(root.resolve(
                "middleware-server/src/main/resources/application.yaml"));
        String compose = Files.readString(root.resolve("docker-compose.yml"));

        assertThat(application)
                .contains("keep-live-enabled: ${OSS_KEEP_LIVE_ENABLED:false}");
        assertThat(compose)
                .contains("jacolp.aliyun.oss.keep-live-enabled: ${OSS_KEEP_LIVE_ENABLED:-false}");
    }

    private static Path locateRepositoryRoot() {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null) {
            if (Files.isRegularFile(directory.resolve("docker-compose.yml"))
                    && Files.isRegularFile(directory.resolve("middleware-server/pom.xml"))) {
                return directory;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }

    @Configuration(proxyBeanMethods = false)
    @Import(AliyunOSSClientKeepLiveTask.class)
    static class TestConfiguration {
    }
}
