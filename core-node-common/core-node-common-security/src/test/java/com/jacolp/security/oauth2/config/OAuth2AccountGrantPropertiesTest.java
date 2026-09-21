package com.jacolp.security.oauth2.config;

import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.common.security.oauth2.config.OAuth2AccountGrantConfiguration;
import com.jacolp.common.security.oauth2.config.OAuth2AccountGrantProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2AccountGrantPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertiesConfiguration.class, OAuth2AccountGrantConfiguration.class));

    @Test
    void defaultsAreTheRequiredImmutableAccountGrantSet() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            OAuth2AccountGrantProperties properties = context.getBean(OAuth2AccountGrantProperties.class);
            assertThat(properties.getDefaultGrantTypes())
                    .containsExactly("password", "email-code", "authorization_code");
            assertThat(properties.getDefaultGrantTypes()).isUnmodifiable();
            assertThat(context.getBeansOfType(AccountGrantTypeResolver.class)).hasSize(1);
            assertThat(context.getBean(AccountGrantTypeResolver.class).defaultGrantTypes())
                    .containsExactly("password", "email-code", "authorization_code");
        });
    }

    @Test
    void bindsCommaSeparatedDefaultsWithTrimNormalization() {
        contextRunner.withPropertyValues(
                        "jacolp.oauth2.account.default-grant-types= password , email-code , authorization_code ")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(OAuth2AccountGrantProperties.class).getDefaultGrantTypes())
                            .containsExactly("password", "email-code", "authorization_code");
                    assertThat(context.getBean(AccountGrantTypeResolver.class).defaultGrantTypes())
                            .containsExactly("password", "email-code", "authorization_code");
                });
    }

    @Test
    void bindsIndexedDefaultGrantList() {
        contextRunner.withPropertyValues(
                        "jacolp.oauth2.account.default-grant-types[0]=password",
                        "jacolp.oauth2.account.default-grant-types[1]=email-code",
                        "jacolp.oauth2.account.default-grant-types[2]=authorization_code")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(OAuth2AccountGrantProperties.class).getDefaultGrantTypes())
                            .containsExactly("password", "email-code", "authorization_code");
                    assertThat(context.getBeansOfType(AccountGrantTypeResolver.class)).hasSize(1);
                });
    }

    @Test
    void rejectsUnsafeOrIncompleteConfiguredDefaults() {
        contextRunner.withPropertyValues("jacolp.oauth2.account.default-grant-types=password,email-code")
                .run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues("jacolp.oauth2.account.default-grant-types=password,email-code,refresh_token")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void letsApplicationsProvideTheOnlyResolverBean() {
        AccountGrantTypeResolver override = new AccountGrantTypeResolver(
                List.of("password", "email-code", "authorization_code"));

        contextRunner.withBean(AccountGrantTypeResolver.class, () -> override)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(AccountGrantTypeResolver.class).values()).containsExactly(override);
                });
    }

    @Test
    void accountGrantResolverRemainsAvailableWithoutCodecConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(AccountGrantTypeResolver.class)).hasSize(1);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OAuth2AccountGrantProperties.class)
    static class PropertiesConfiguration {
    }
}
