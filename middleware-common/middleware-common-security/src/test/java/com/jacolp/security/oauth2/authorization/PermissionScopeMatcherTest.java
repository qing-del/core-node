package com.jacolp.security.oauth2.authorization;

import com.jacolp.common.security.oauth2.authorization.PermissionScopeMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PermissionScopeMatcherTest {

    @Test
    void wildcardGrantCoversSpecificRequiredScopeWithoutExpansion() {
        assertThat(PermissionScopeMatcher.grants(List.of("*:read"), "note:read")).isTrue();
        assertThat(PermissionScopeMatcher.grants(List.of("note:*"), "note:read")).isTrue();
        assertThat(PermissionScopeMatcher.grants(List.of("note:read"), "note:read")).isTrue();
        assertThat(PermissionScopeMatcher.grants(List.of("*:read"), "*:read")).isTrue();
    }

    @Test
    void specificGrantDoesNotCoverBroaderOrDifferentRequirement() {
        assertThat(PermissionScopeMatcher.grants(List.of("note:read"), "*:read")).isFalse();
        assertThat(PermissionScopeMatcher.grants(List.of("note:read"), "media:read")).isFalse();
        assertThat(PermissionScopeMatcher.grants(List.of("note:write"), "note:read")).isFalse();
        assertThat(PermissionScopeMatcher.grantsAll(List.of("*:read", "note:write"),
                List.of("note:read", "note:write"))).isTrue();
    }

    @Test
    void malformedGrantedAndRequiredScopesFailClosed() {
        assertThatIllegalArgumentException().isThrownBy(() -> PermissionScopeMatcher.grants(List.of(" note:read"), "note:read"));
        assertThatIllegalArgumentException().isThrownBy(() -> PermissionScopeMatcher.grants(List.of("note:read"), "note:read:more"));
        assertThatIllegalArgumentException().isThrownBy(() -> PermissionScopeMatcher.grants(List.of("note:*read"), "note:read"));
    }
}
