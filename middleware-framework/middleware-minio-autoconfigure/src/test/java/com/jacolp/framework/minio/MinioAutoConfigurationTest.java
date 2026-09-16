package com.jacolp.framework.minio;

import static org.assertj.core.api.Assertions.assertThat;

import io.minio.MinioClient;
import java.net.Proxy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MinioAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MinioAutoConfiguration.class));

    @Test
    void createsClientAndBindsLogicalBuckets() {
        contextRunner.withPropertyValues(
                "jacolp.minio.endpoint=http://localhost:9000",
                "jacolp.minio.access-key=test-access-key",
                "jacolp.minio.secret-key=test-secret-key",
                "jacolp.minio.bucket.document=middleware-document")
                .run(context -> {
                    assertThat(context).hasSingleBean(MinioClient.class);
                    assertThat(context).hasSingleBean(MinioBucketResolver.class);
                    assertThat(context).hasSingleBean(MinioObjectStorage.class);
                    assertThat(context.getBean(MinioProperties.class).getBucket())
                            .containsEntry("document", "middleware-document");
                    assertThat(context.getBean(MinioBucketResolver.class).requireBucket("document"))
                            .isEqualTo("middleware-document");
                });
    }

    @Test
    void disablesSystemProxyByDefault() {
        contextRunner.withPropertyValues(
                "jacolp.minio.endpoint=http://localhost:9000",
                "jacolp.minio.access-key=test-access-key",
                "jacolp.minio.secret-key=test-secret-key")
                .run(context -> {
                    assertThat(context.getBean(MinioProperties.class).isUseSystemProxy()).isFalse();
                    assertThat(MinioAutoConfiguration.createHttpClient(false).proxy())
                            .isEqualTo(Proxy.NO_PROXY);
                });
    }

    @Test
    void canEnableSystemProxyFromConfiguration() {
        contextRunner.withPropertyValues(
                "jacolp.minio.endpoint=http://localhost:9000",
                "jacolp.minio.access-key=test-access-key",
                "jacolp.minio.secret-key=test-secret-key",
                "jacolp.minio.use-system-proxy=true")
                .run(context -> {
                    assertThat(context.getBean(MinioProperties.class).isUseSystemProxy()).isTrue();
                    // A null fixed proxy means OkHttp will consult the JVM default ProxySelector.
                    assertThat(MinioAutoConfiguration.createHttpClient(true).proxy()).isNull();
                });
    }
}
