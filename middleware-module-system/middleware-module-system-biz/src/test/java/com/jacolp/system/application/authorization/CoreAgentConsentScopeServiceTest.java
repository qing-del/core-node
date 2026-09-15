package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.CoreAgentConsentScopeOptions;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreAgentConsentScopeServiceTest {

    private final CoreAgentConsentScopeService service = new CoreAgentConsentScopeService(new OAuth2ScopeResolver());

    @Test
    void omittedScopeBuildsAllNonSuperCandidatesAndServerMandatoryAutoApprove() {
        CoreAgentConsentScopeOptions options = service.options(role("USER", "*:read", "note:write", "*:super"),
                policy(Set.of("*:read", "note:write", "*:super"), Set.of("note:read")), null, null);

        assertThat(options.candidateScopes()).containsExactly("*:read", "note:write");
        assertThat(options.mandatoryScopes()).containsExactly("note:read");
        assertThat(options.optionalScopes()).containsExactly("*:read", "note:write");
        assertThat(options.preselectedOptionalScopes()).isEmpty();
    }

    @Test
    void explicitScopeUsesRequestedIntersectionAndOnlyCreatorMayRequestSuper() {
        CoreAgentRegisteredClientPolicy policy = policy(Set.of("note:write", "*:super"), Set.of());

        Assertions.assertThat(service.options(role("USER", "note:write", "*:super"), policy,
                List.of("note:write", "*:super"), null).candidateScopes()).containsExactly("note:write");
        Assertions.assertThat(service.options(role("CREATOR", "note:write", "*:super"), policy,
                List.of("note:write", "*:super"), null).candidateScopes())
                .containsExactly("*:super", "note:write");
    }

    @Test
    void confirmAlwaysRestoresMandatoryScopesAndRequiresAnOptionalExactSubset() {
        CoreAgentRegisteredClientPolicy policy = policy(Set.of("*:read", "note:write"), Set.of("note:read"));

        assertThat(service.confirm(role("USER", "*:read", "note:write"), policy, null, null,
                List.of("note:write"))).containsExactly("note:read", "note:write");
        assertThatIllegalArgumentException().isThrownBy(() -> service.confirm(role("USER", "*:read", "note:write"),
                policy, null, null, List.of("note:read")));
    }

    @Test
    void existingConsentOnlyPreselectsStillOptionalExactScopes() {
        CoreAgentConsentScopeOptions options = service.options(role("USER", "*:read", "note:write"),
                policy(Set.of("*:read", "note:write"), Set.of("note:read")), null,
                List.of("media:read", "note:write", "note:read"));

        assertThat(options.preselectedOptionalScopes()).containsExactly("note:write");
    }

    @Test
    void rejectTamperedDuplicateNullAndEmptySubmissions() {
        CoreAgentRegisteredClientPolicy noMandatory = policy(Set.of("note:write"), Set.of());
        EffectiveRolePermissions role = role("USER", "note:write");

        assertThatIllegalArgumentException().isThrownBy(() -> service.confirm(role, noMandatory,
                List.of("note:write"), null, List.of("media:read")));
        assertThatIllegalArgumentException().isThrownBy(() -> service.confirm(role, noMandatory,
                List.of("note:write"), null, List.of("note:write", "note:write")));
        assertThatIllegalArgumentException().isThrownBy(() -> service.confirm(role, noMandatory,
                List.of("note:write"), null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.confirm(role, noMandatory,
                List.of("note:write"), null, List.of()));
    }

    @Test
    void wildcardOverlapIsPreservedWithoutPatternSubtraction() {
        CoreAgentConsentScopeOptions options = service.options(role("USER", "*:read"),
                policy(Set.of("*:read"), Set.of("note:read")), null, null);

        assertThat(options.mandatoryScopes()).containsExactly("note:read");
        assertThat(options.optionalScopes()).containsExactly("*:read");
        assertThat(service.confirm(role("USER", "*:read"), policy(Set.of("*:read"), Set.of("note:read")),
                null, null, List.of("*:read"))).containsExactly("*:read", "note:read");
    }

    @Test
    void defaultFlowFailsClosedWhenClientAutoApproveConfigurationIsPolluted() {
        assertThatIllegalStateException().isThrownBy(() -> service.options(role("USER", "note:read"),
                policy(Set.of("note:read"), Set.of()), null, null));
    }

    @Test
    void optionsAreSortedDefensivelyCopiedAndPreselectionMustBeOptional() {
        CoreAgentConsentScopeOptions options = new CoreAgentConsentScopeOptions(List.of("note:write", "note:read"),
                List.of("note:read"), List.of("note:write"), List.of("note:write"));

        assertThat(options.candidateScopes()).containsExactly("note:read", "note:write");
        assertThatThrownBy(() -> options.candidateScopes().add("media:read"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> new CoreAgentConsentScopeOptions(List.of(), List.of(),
                List.of("note:read"), List.of("note:write")));
    }

    @Test
    void emptyCandidateAndExplicitEmptyRequestAreRejected() {
        CoreAgentRegisteredClientPolicy policy = policy(Set.of("note:read"), Set.of("note:read"));
        assertThatIllegalArgumentException().isThrownBy(() -> service.options(role("USER", "media:read"), policy,
                List.of("note:read"), null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.options(role("USER", "note:read"), policy,
                List.of(), null));
    }

    private static CoreAgentRegisteredClientPolicy policy(Set<String> scopes, Set<String> autoApproveScopes) {
        return new CoreAgentRegisteredClientPolicy("registered-core-agent", "core_agent",
                "http://127.0.0.1:9090/oauth/callback", scopes, autoApproveScopes, "127.0.0.1",
                Duration.ofHours(1), Duration.ofHours(24), Duration.ofMinutes(10));
    }

    private static EffectiveRolePermissions role(String roleCode, String... scopes) {
        return new EffectiveRolePermissions(2L, roleCode, 2, List.of(scopes));
    }
}
