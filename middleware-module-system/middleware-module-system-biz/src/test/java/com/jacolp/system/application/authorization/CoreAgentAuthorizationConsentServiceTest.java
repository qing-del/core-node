package com.jacolp.system.application.authorization;

import com.jacolp.common.core.exception.AuthenticationException;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationConsentDecision;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.port.out.CoreAgentAuthorizationConsentStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreAgentAuthorizationConsentServiceTest {

    @Test
    void firstConsentRequiresUserSubmission() {
        Fixture fixture = fixture();
        when(fixture.store.findScopes(registeredClientId(), "7")).thenReturn(Optional.empty());

        CoreAgentAuthorizationConsentDecision decision = fixture.service.prepare(7L, role("note:read", "note:write"),
                policy(Set.of("note:read", "note:write"), Set.of("note:read")), null);

        assertThat(decision.consentRequired()).isTrue();
        assertThat(decision.reusedFinalScopes()).isEmpty();
        assertThat(decision.options().mandatoryScopes()).containsExactly("note:read");
        assertThat(decision.options().optionalScopes()).containsExactly("note:write");
    }

    @Test
    void existingExactOrWildcardCoverageReusesCurrentCandidateScopes() {
        Fixture exact = fixture();
        when(exact.store.findScopes(registeredClientId(), "7")).thenReturn(Optional.of(linkedSet("note:read")));
        assertThat(exact.service.prepare(7L, role("note:read"), policy(Set.of("note:read"), Set.of("note:read")),
                null).reusedFinalScopes()).containsExactly("note:read");

        Fixture wildcard = fixture();
        when(wildcard.store.findScopes(registeredClientId(), "7")).thenReturn(Optional.of(linkedSet("*:read")));
        CoreAgentAuthorizationConsentDecision decision = wildcard.service.prepare(7L, role("note:read"),
                policy(Set.of("note:read"), Set.of("note:read")), null);
        assertThat(decision.consentRequired()).isFalse();
        assertThat(decision.reusedFinalScopes()).containsExactly("note:read");
    }

    @Test
    void missingOptionalOrMandatoryCandidateRequiresFreshConsent() {
        Fixture optionalMissing = fixture();
        when(optionalMissing.store.findScopes(registeredClientId(), "7")).thenReturn(Optional.of(linkedSet("note:read")));
        assertThat(optionalMissing.service.prepare(7L, role("note:read", "note:write"),
                policy(Set.of("note:read", "note:write"), Set.of("note:read")), null).consentRequired()).isTrue();

        Fixture mandatoryMissing = fixture();
        when(mandatoryMissing.store.findScopes(registeredClientId(), "7")).thenReturn(Optional.of(linkedSet("note:write")));
        assertThat(mandatoryMissing.service.prepare(7L, role("note:read", "note:write"),
                policy(Set.of("note:read", "note:write"), Set.of("note:read")), null).consentRequired()).isTrue();
    }

    @Test
    void confirmForcesMandatoryAutoApproveAndPreservesHistoryOutsideCurrentCandidates() {
        Fixture fixture = fixture();
        when(fixture.store.findScopes(registeredClientId(), "7"))
                .thenReturn(Optional.of(linkedSet("media:read", "note:write")));
        CoreAgentRegisteredClientPolicy policy = policy(Set.of("note:read", "note:write"), Set.of("note:read"));

        assertThat(fixture.service.confirm(7L, role("note:read", "note:write"), policy, null,
                List.of("note:write"))).containsExactly("note:read", "note:write");
        ArgumentCaptor<Collection<String>> saved = ArgumentCaptor.forClass(Collection.class);
        verify(fixture.store).saveScopes(eq(registeredClientId()), eq("7"), saved.capture());
        assertThat(saved.getValue()).containsExactly("media:read", "note:read", "note:write");
    }

    @Test
    void cancellingAnOptionalScopeRemovesOnlyItsExactCurrentCandidateAndKeepsOutsideHistory() {
        Fixture fixture = fixture();
        when(fixture.store.findScopes(registeredClientId(), "7"))
                .thenReturn(Optional.of(linkedSet("media:read", "note:write")));
        CoreAgentRegisteredClientPolicy policy = policy(Set.of("note:read", "note:write"), Set.of("note:read"));

        assertThat(fixture.service.confirm(7L, role("note:read", "note:write"), policy, null, List.of()))
                .containsExactly("note:read");
        ArgumentCaptor<Collection<String>> saved = ArgumentCaptor.forClass(Collection.class);
        verify(fixture.store).saveScopes(eq(registeredClientId()), eq("7"), saved.capture());
        assertThat(saved.getValue()).containsExactly("media:read", "note:read");
    }

    @Test
    void wildcardOverlapIsStoredAsPatternsWithoutExpansionOrMinimization() {
        Fixture fixture = fixture();
        when(fixture.store.findScopes(registeredClientId(), "7")).thenReturn(Optional.empty());
        CoreAgentRegisteredClientPolicy policy = policy(Set.of("*:read"), Set.of("note:read"));

        assertThat(fixture.service.confirm(7L, role("*:read"), policy, null, List.of("*:read")))
                .containsExactly("*:read", "note:read");
        ArgumentCaptor<Collection<String>> saved = ArgumentCaptor.forClass(Collection.class);
        verify(fixture.store).saveScopes(eq(registeredClientId()), eq("7"), saved.capture());
        assertThat(saved.getValue()).containsExactly("*:read", "note:read");
    }

    @Test
    void confirmRereadsExistingConsentAfterPrepareToAvoidTimeOfCheckTimeOfUse() {
        Fixture fixture = fixture();
        when(fixture.store.findScopes(registeredClientId(), "7")).thenReturn(Optional.empty(),
                Optional.of(linkedSet("note:write")));
        CoreAgentRegisteredClientPolicy policy = policy(Set.of("note:read", "note:write"), Set.of("note:read"));

        fixture.service.prepare(7L, role("note:read", "note:write"), policy, null);
        fixture.service.confirm(7L, role("note:read", "note:write"), policy, null, List.of());

        ArgumentCaptor<Collection<String>> saved = ArgumentCaptor.forClass(Collection.class);
        verify(fixture.store).saveScopes(eq(registeredClientId()), eq("7"), saved.capture());
        assertThat(saved.getValue()).containsExactly("note:read");
        verify(fixture.store, org.mockito.Mockito.times(2)).findScopes(registeredClientId(), "7");
    }

    @Test
    void nullPollutedAndStoreFailureResultsFailClosedWithoutConvertingInfrastructureErrors() {
        Fixture nullResult = fixture();
        when(nullResult.store.findScopes(registeredClientId(), "7")).thenReturn(null);
        assertThatIllegalStateException().isThrownBy(() -> nullResult.service.prepare(7L, role("note:read"),
                policy(Set.of("note:read"), Set.of("note:read")), null));

        Fixture polluted = fixture();
        when(polluted.store.findScopes(registeredClientId(), "7")).thenReturn(Optional.of(linkedSet("bad")));
        assertThatIllegalStateException().isThrownBy(() -> polluted.service.prepare(7L, role("note:read"),
                policy(Set.of("note:read"), Set.of("note:read")), null));

        Fixture failure = fixture();
        IllegalStateException unavailable = new IllegalStateException("database unavailable");
        when(failure.store.findScopes(registeredClientId(), "7")).thenThrow(unavailable);
        assertThatThrownBy(() -> failure.service.prepare(7L, role("note:read"),
                policy(Set.of("note:read"), Set.of("note:read")), null)).isSameAs(unavailable);
    }

    @Test
    void scopeTamperingUsesUniformPiiFreeAuthenticationRejectionAndDecisionIsImmutableAndRedacted() {
        Fixture fixture = fixture();
        when(fixture.store.findScopes(registeredClientId(), "7")).thenReturn(Optional.empty());
        CoreAgentRegisteredClientPolicy policy = policy(Set.of("note:read", "note:write"), Set.of("note:read"));

        assertThatThrownBy(() -> fixture.service.confirm(7L, role("note:read", "note:write"), policy, null,
                List.of("media:read"))).isInstanceOf(AuthenticationException.class)
                .hasMessage(CoreAgentAuthorizationConsentRejectedException.MESSAGE);
        CoreAgentAuthorizationConsentDecision decision = fixture.service.prepare(7L, role("note:read"),
                policy(Set.of("note:read"), Set.of("note:read")), null);
        assertThatThrownBy(() -> decision.options().candidateScopes().add("media:read"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(decision.toString()).doesNotContain("note:read", "alice", "password");
    }

    private static Fixture fixture() {
        CoreAgentAuthorizationConsentStore store = mock(CoreAgentAuthorizationConsentStore.class);
        return new Fixture(store, new CoreAgentAuthorizationConsentService(
                new CoreAgentConsentScopeService(new OAuth2ScopeResolver()), store));
    }

    private static EffectiveRolePermissions role(String... permissions) {
        return new EffectiveRolePermissions(2L, "USER", 2, List.of(permissions));
    }

    private static CoreAgentRegisteredClientPolicy policy(Set<String> scopes, Set<String> autoApprove) {
        return new CoreAgentRegisteredClientPolicy(registeredClientId(), "core_agent",
                "http://127.0.0.1:9090/oauth/callback", scopes, autoApprove, "0.0.0.0/0",
                Duration.ofHours(1), Duration.ofHours(24), Duration.ofMinutes(10));
    }

    private static String registeredClientId() {
        return "e7cf5b30-8e43-4db2-bc53-000000000003";
    }

    private static Set<String> linkedSet(String... scopes) {
        return new LinkedHashSet<>(List.of(scopes));
    }

    private record Fixture(CoreAgentAuthorizationConsentStore store, CoreAgentAuthorizationConsentService service) {
    }
}
