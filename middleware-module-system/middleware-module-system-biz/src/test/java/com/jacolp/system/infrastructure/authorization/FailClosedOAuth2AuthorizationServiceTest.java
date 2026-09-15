package com.jacolp.system.infrastructure.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class FailClosedOAuth2AuthorizationServiceTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ServiceOnlyConfiguration.class);

    @Test
    void everySasPersistenceOperationFailsClosedWithoutInspectingSensitiveInput() {
        FailClosedOAuth2AuthorizationService service = new FailClosedOAuth2AuthorizationService();

        assertRejected(() -> service.save(null));
        assertRejected(() -> service.remove(null));
        assertRejected(() -> service.findById("authorization-id"));
        assertRejected(() -> service.findByToken("raw-token-value", null));
        assertThat(service.toString()).doesNotContain("authorization-id", "raw-token-value");
    }

    @Test
    void contextAlwaysContainsOnlyThisExplicitAuthorizationService() {
        runner.run(context -> {
            assertThat(context.getBeansOfType(OAuth2AuthorizationService.class)).hasSize(1);
            assertThat(context.getBean(OAuth2AuthorizationService.class))
                    .isInstanceOf(FailClosedOAuth2AuthorizationService.class);
        });
    }

    @Test
    void staticBoundaryHasNoDelegateOrStateThatCouldBecomeAnInMemoryOrJdbcAuthorizationStore() {
        assertThat(FailClosedOAuth2AuthorizationService.class.getInterfaces())
                .containsExactly(OAuth2AuthorizationService.class);
        assertThat(FailClosedOAuth2AuthorizationService.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .containsExactly("FAILURE_MESSAGE");
    }

    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatIllegalStateException().isThrownBy(operation)
                .withMessage(FailClosedOAuth2AuthorizationService.FAILURE_MESSAGE);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(FailClosedOAuth2AuthorizationService.class)
    static class ServiceOnlyConfiguration {
    }
}
