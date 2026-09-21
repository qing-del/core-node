package com.jacolp.system.application.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class EmailLoginBindingFingerprintContextTest {

    @Test
    void registersExactlyOneInjectableFingerprintBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(EmailLoginBindingFingerprint.class);
            context.refresh();

            assertThat(context.getBeansOfType(EmailLoginBindingFingerprint.class)).hasSize(1);
            assertThat(context.getBean(EmailLoginBindingFingerprint.class)).isNotNull();
        }
    }
}
