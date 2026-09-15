package com.jacolp.security.oauth2.config;

import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountGrantTypeResolverTest {

    private final AccountGrantTypeResolver resolver = new AccountGrantTypeResolver(
            List.of("password", "email-code", "authorization_code"));

    @Test
    void combinesTrimmedExtraCsvIntoAStableImmutableSet() {
        Set<String> effective = resolver.effectiveGrantTypes(" custom-grant , custom.other ");

        assertThat(effective).containsExactly("password", "email-code", "authorization_code", "custom-grant", "custom.other");
        assertThatThrownBy(() -> effective.add("late-grant")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(resolver.allows(" custom-grant ", "custom-grant")).isTrue();
        assertThat(resolver.allows("Custom-grant", "custom-grant")).isFalse();
    }

    @Test
    void treatsBlankExtraCsvAsNoAddition() {
        assertThat(resolver.effectiveGrantTypes(null)).containsExactly("password", "email-code", "authorization_code");
        assertThat(resolver.effectiveGrantTypes("  ")).containsExactly("password", "email-code", "authorization_code");
    }

    @Test
    void rejectsExtraDuplicatesDefaultsRefreshAndMalformedItems() {
        assertThatIllegalArgumentException().isThrownBy(() -> resolver.effectiveGrantTypes("password"));
        assertThatIllegalArgumentException().isThrownBy(() -> resolver.effectiveGrantTypes("custom, custom"));
        assertThatIllegalArgumentException().isThrownBy(() -> resolver.effectiveGrantTypes("refresh_token"));
        assertThatIllegalArgumentException().isThrownBy(() -> resolver.effectiveGrantTypes("custom,,other"));
        assertThatIllegalArgumentException().isThrownBy(() -> resolver.effectiveGrantTypes("custom grant"));
    }

    @Test
    void requiresExactlyTheDocumentedDefaultGrantTypes() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AccountGrantTypeResolver(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new AccountGrantTypeResolver(
                List.of("password", "email-code")));
        assertThatIllegalArgumentException().isThrownBy(() -> new AccountGrantTypeResolver(
                List.of("password", "email-code", "authorization_code", "password")));
        assertThatIllegalArgumentException().isThrownBy(() -> new AccountGrantTypeResolver(
                List.of("password", "email-code", "authorization_code", "PASSWORD")));
        assertThatIllegalArgumentException().isThrownBy(() -> new AccountGrantTypeResolver(
                List.of("password", "email-code", "authorization code")));
    }
}
