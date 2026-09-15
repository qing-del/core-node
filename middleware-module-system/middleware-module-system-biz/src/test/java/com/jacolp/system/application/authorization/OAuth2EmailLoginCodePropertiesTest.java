package com.jacolp.system.application.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuth2EmailLoginCodePropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void defaultsAreTheMaximumAllowedPolicy() {
        runner.run(context -> {
            OAuth2EmailLoginCodeProperties properties =
                    context.getBean(OAuth2EmailLoginCodeProperties.class);

            assertThat(properties.getCodeTtl()).isEqualTo(Duration.ofMinutes(10));
            assertThat(properties.getIssueCooldown()).isEqualTo(Duration.ofSeconds(60));
            assertThat(properties.getIssueWindow()).isEqualTo(Duration.ofHours(1));
            assertThat(properties.getMaxIssuesPerWindow()).isEqualTo(5);
            assertThat(properties.getMaxFailedAttempts()).isEqualTo(5);
        });
    }

    @Test
    void bindsWholeMinuteOneToTenMinuteTtlsAndStricterLimits() {
        runner.withPropertyValues(
                        "jacolp.oauth2.email-code.code-ttl=PT1M",
                        "jacolp.oauth2.email-code.issue-cooldown=PT2M",
                        "jacolp.oauth2.email-code.issue-window=PT2H",
                        "jacolp.oauth2.email-code.max-issues-per-window=3",
                        "jacolp.oauth2.email-code.max-failed-attempts=2")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    OAuth2EmailLoginCodeProperties properties =
                            context.getBean(OAuth2EmailLoginCodeProperties.class);
                    assertThat(properties.getCodeTtl()).isEqualTo(Duration.ofMinutes(1));
                    assertThat(properties.getMaxIssuesPerWindow()).isEqualTo(3);
                    assertThat(properties.getMaxFailedAttempts()).isEqualTo(2);
                });
    }

    @Test
    void rejectsEveryWiderBoundary() {
        for (String property : new String[]{
                "code-ttl=PT0S",
                "code-ttl=PT30S",
                "code-ttl=PT11M",
                "issue-cooldown=PT59S",
                "issue-window=PT59M",
                "max-issues-per-window=0",
                "max-issues-per-window=6",
                "max-failed-attempts=0",
                "max-failed-attempts=6"}) {
            runner.withPropertyValues("jacolp.oauth2.email-code." + property)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void rejectsCooldownLongerThanIssueWindow() {
        runner.withPropertyValues("jacolp.oauth2.email-code.issue-cooldown=PT2H")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void settersRejectNullAndValidationRejectsInvalidCodeTtls() {
        OAuth2EmailLoginCodeProperties properties = new OAuth2EmailLoginCodeProperties();

        assertThatThrownBy(() -> properties.setCodeTtl(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> properties.setMaxIssuesPerWindow(null)).isInstanceOf(NullPointerException.class);
        properties.setCodeTtl(Duration.ofSeconds(-1));
        assertThatIllegalArgumentException().isThrownBy(properties::validate);

        properties.setCodeTtl(Duration.ofSeconds(Long.MAX_VALUE));
        assertThatIllegalArgumentException().isThrownBy(properties::validate);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OAuth2EmailLoginCodeProperties.class)
    static class Config {
    }
}
