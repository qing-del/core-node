package com.jacolp.document.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class YjsMergeServicePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DocumentModuleConfiguration.class);

    @Test
    void shouldBindMergeServiceBaseUrl() {
        contextRunner.withPropertyValues("jacolp.yjs-merge-service.base-url=http://localhost:3100")
                .run(context -> assertThat(context.getBean(YjsMergeServiceProperties.class).requireBaseUrl())
                        .isEqualTo("http://localhost:3100"));
    }

    @Test
    void shouldRejectMissingMergeServiceBaseUrlWhenRequired() {
        contextRunner.run(context -> assertThatIllegalStateException()
                .isThrownBy(() -> context.getBean(YjsMergeServiceProperties.class).requireBaseUrl()));
    }
}
