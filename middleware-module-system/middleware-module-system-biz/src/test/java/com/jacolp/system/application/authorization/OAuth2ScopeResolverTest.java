package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.PermissionScopePattern;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuth2ScopeResolverTest {

    private final OAuth2ScopeResolver resolver = new OAuth2ScopeResolver();

    @Test
    void patternMeetKeepsTheMoreSpecificComponentInEitherDirection() {
        PermissionScopePattern wildcard = PermissionScopePattern.parse("*:read");
        PermissionScopePattern specific = PermissionScopePattern.parse("note:read");

        assertThat(wildcard.meet(specific)).contains(specific);
        assertThat(specific.meet(wildcard)).contains(specific);
        assertThat(wildcard.meet(PermissionScopePattern.parse("*:read"))).contains(wildcard);
        assertThat(PermissionScopePattern.parse("note:read")
                .meet(PermissionScopePattern.parse("media:read"))).isEmpty();
        assertThat(PermissionScopePattern.parse("Note:read")
                .meet(PermissionScopePattern.parse("note:read"))).isEmpty();
    }

    @Test
    void documentedPatternIntersectionProducesTheSpecificJwtScopeWithoutExpansion() {
        List<String> scopes = resolver.resolve(role("USER", "*:read"), List.of("note:read"), List.of("*:read"),
                List.of("note:read"));

        assertThat(scopes).containsExactly("note:read");
        assertThatThrownBy(() -> scopes.add("media:read")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void bothWildcardsRemainWildcardsAndResultIsNaturallySorted() {
        assertThat(resolver.resolve(role("USER", "*:write", "*:read"), List.of("*:write", "*:read"),
                List.of("*:read", "*:write"), List.of("*:write", "*:read")))
                .containsExactly("*:read", "*:write");
    }

    @Test
    void nullRequestUsesAutoApproveButAnExplicitEmptyRequestProducesNoScopes() {
        EffectiveRolePermissions role = role("USER", "*:read");

        assertThat(resolver.resolve(role, List.of("*:read"), List.of("note:read"), null))
                .containsExactly("note:read");
        assertThat(resolver.resolve(role, List.of("*:read"), List.of("note:read"), List.of())).isEmpty();
    }

    @Test
    void superRequiresCreatorAndAnExplicitIntersectingSuperRequestAndNeverComesFromAutoApprove() {
        assertThat(resolver.resolve(role("ADMIN", "*:super"), List.of("*:super"), List.of(),
                List.of("*:super"))).isEmpty();
        assertThat(resolver.resolve(role("CREATOR", "*:super"), List.of("note:super"), List.of(),
                List.of("*:super"))).containsExactly("note:super");
        assertThat(resolver.resolve(role("CREATOR", "*:super"), List.of("note:super"), List.of("note:super"),
                null)).isEmpty();
        assertThat(resolver.resolve(role("CREATOR", "*:super"), List.of("note:super"), List.of(),
                List.of("note:*"))).isEmpty();
    }

    @Test
    void malformedNullBlankAndDuplicateInputsFailClosedAfterTrimming() {
        assertThatIllegalArgumentException().isThrownBy(() -> PermissionScopePattern.parse(null));
        assertThatIllegalArgumentException().isThrownBy(() -> PermissionScopePattern.parse("note:read:more"));
        assertThatIllegalArgumentException().isThrownBy(() -> PermissionScopePattern.parse("note: "));
        assertThatIllegalArgumentException().isThrownBy(() -> PermissionScopePattern.parse("note:*read"));
        assertThatIllegalArgumentException().isThrownBy(() -> resolver.resolve(role("USER", "*:read"),
                List.of("note:read", " note:read "), List.of(), List.of("note:read")));
        assertThatIllegalArgumentException().isThrownBy(() -> resolver.resolve(role("USER", "*:read"),
                null, List.of(), List.of("note:read")));
        assertThatIllegalArgumentException().isThrownBy(() -> resolver.resolve(role("USER", "*:read"),
                Arrays.asList((String) null), List.of(), List.of("note:read")));
    }

    @Test
    void wildcardPatternsAreNeverExpandedThroughAnyPermissionCatalogue() {
        assertThat(resolver.resolve(role("USER", "*:read"), List.of("*:read"), List.of("*:read"), null))
                .containsExactly("*:read");
    }

    @Test
    void refreshScopeNarrowingMeetsCurrentAndExplicitRequestsWithoutExpansion() {
        assertThat(resolver.narrowGrantedScopes(List.of("*:read"), List.of("note:read")))
                .containsExactly("note:read");
        assertThat(resolver.narrowGrantedScopes(List.of("note:read"), List.of("*:read")))
                .containsExactly("note:read");
        assertThat(resolver.narrowGrantedScopes(List.of("note:read", "sys:read"), List.of("note:read")))
                .containsExactly("note:read");
        List<String> immutable = resolver.narrowGrantedScopes(List.of("*:read"), List.of("*:read"));
        assertThatThrownBy(() -> immutable.add("media:read")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void refreshScopeNarrowingAllowsExplicitEmptyAndRejectsNullDuplicateOrInvalidPatterns() {
        assertThat(resolver.narrowGrantedScopes(List.of("note:read"), List.of())).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> resolver.narrowGrantedScopes(null, List.of("note:read")));
        assertThatIllegalArgumentException().isThrownBy(() -> resolver.narrowGrantedScopes(List.of("note:read"), null));
        assertThatIllegalArgumentException().isThrownBy(() -> resolver.narrowGrantedScopes(List.of("note:read", "note:read"),
                List.of("note:read")));
        assertThatIllegalArgumentException().isThrownBy(() -> resolver.narrowGrantedScopes(List.of("note:read"),
                List.of("not a scope")));
    }

    private static EffectiveRolePermissions role(String roleCode, String... permissions) {
        return new EffectiveRolePermissions(3L, roleCode, 3, List.of(permissions));
    }
}
