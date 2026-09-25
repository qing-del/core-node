package com.jacolp.document.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class YjsServicePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DocumentModuleConfiguration.class);

    @Test
    void shouldBindYjsServiceBaseUrl() {
        contextRunner.withPropertyValues("jacolp.yjs-service.base-url=http://localhost:3100")
                .run(context -> assertThat(context.getBean(YjsServiceProperties.class).requireBaseUrl())
                        .isEqualTo("http://localhost:3100"));
    }

    @Test
    void shouldRejectMissingYjsServiceBaseUrlWhenRequired() {
        contextRunner.run(context -> assertThatIllegalStateException()
                .isThrownBy(() -> context.getBean(YjsServiceProperties.class).requireBaseUrl()));
    }
}
