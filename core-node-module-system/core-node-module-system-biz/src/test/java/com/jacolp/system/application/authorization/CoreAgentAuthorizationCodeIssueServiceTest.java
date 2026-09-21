package com.jacolp.system.application.authorization;

import com.jacolp.constant.UserConstant;
import com.jacolp.common.core.exception.AuthenticationException;
import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.common.security.oauth2.token.SecureOAuth2TokenGenerator;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.system.application.port.out.CoreAgentPendingAuthorizationCodeTransitionStore;
import com.jacolp.system.application.port.out.CoreAgentPendingAuthorizationStore;
import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationCodeIssueRequest;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationCodeState;
import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationConversionRequest;
import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationState;
import com.jacolp.system.application.authorization.model.CoreAgentPreparedPendingAuthorization;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreAgentAuthorizationCodeIssueServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T05:00:00Z");
    private static final String RAW_CODE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String ALTERNATE_RAW_CODE = RAW_CODE.substring(0, RAW_CODE.length() - 1) + "Q";
    private static final String CHALLENGE = RAW_CODE;

    @Test
    void issuesAFullStateOnlyThroughPendingThenAtomicCodeTransition() {
        Fixture fixture = fixture();
        when(fixture.transitionStore.consumePendingAndStoreCode(any(), any(), any())).thenReturn(true);

        IssuedCoreAgentAuthorizationCode issued = fixture.service.issue(request(null, List.of("note:write")));

        assertThat(issued.rawCode()).isEqualTo(RAW_CODE);
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        ArgumentCaptor<CoreAgentAuthorizationCodeState> stateCaptor = ArgumentCaptor.forClass(
                CoreAgentAuthorizationCodeState.class);
        ArgumentCaptor<CoreAgentPendingAuthorizationState> pendingCaptor = ArgumentCaptor.forClass(
                CoreAgentPendingAuthorizationState.class);
        verify(fixture.pendingStore).save(any(), pendingCaptor.capture());
        verify(fixture.transitionStore).consumePendingAndStoreCode(any(), eq(pendingCaptor.getValue()), stateCaptor.capture());
        CoreAgentAuthorizationCodeState state = stateCaptor.getValue();
        assertThat(state.clientId()).isEqualTo("core_agent");
        assertThat(state.redirectUri()).isEqualTo("http://127.0.0.1:9090/oauth/callback");
        assertThat(state.scopes()).containsExactly("note:read", "note:write");
        assertThat(state.codeChallenge()).isEqualTo(CHALLENGE);
        assertThat(state.codeChallengeMethod()).isEqualTo("S256");
        assertThat(state.originalSocketAddress()).isEqualTo("127.0.0.1");
        assertThat(state.oauthState()).isEqualTo("browser-state");
        assertThat(state.issuedAt()).isEqualTo(NOW);
        assertThat(state.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(state.accountSnapshot().toString()).contains("passwordHash=<redacted>");
        assertThat(state.accountSnapshot()).hasFieldOrPropertyWithValue("userId", 7L)
                .hasFieldOrPropertyWithValue("username", "alice")
                .hasFieldOrPropertyWithValue("roleId", 2L)
                .hasFieldOrPropertyWithValue("email", "alice@example.test")
                .hasFieldOrPropertyWithValue("extraGrantTypes", "agent_client");

        InOrder order = inOrder(fixture.policyResolver, fixture.accountRepository, fixture.rolePermissionResolver,
                fixture.pendingStore, fixture.transitionStore);
        order.verify(fixture.policyResolver).resolve(CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID);
        order.verify(fixture.accountRepository).findById(7L);
        order.verify(fixture.rolePermissionResolver).resolve(2L);
        order.verify(fixture.pendingStore).save(any(), any(CoreAgentPendingAuthorizationState.class));
        order.verify(fixture.transitionStore).consumePendingAndStoreCode(any(), any(), any());
    }

    @Test
    void rejectsMissingInactiveOrAuthorizationCodeIneligibleAccountsWithoutIssuing() {
        Fixture missing = fixture();
        when(missing.accountRepository.findById(7L)).thenReturn(Optional.empty());
        assertRejected(() -> missing.service.issue(request(null, List.of("note:write"))));

        Fixture inactive = fixture();
        when(inactive.accountRepository.findById(7L)).thenReturn(Optional.of(account(9)));
        assertRejected(() -> inactive.service.issue(request(null, List.of("note:write"))));

        AccountGrantTypeResolver deniedGrantResolver = mock(AccountGrantTypeResolver.class);
        when(deniedGrantResolver.allows(eq(AccountGrantTypeResolver.AUTHORIZATION_CODE), any())).thenReturn(false);
        Fixture grantDenied = fixture(deniedGrantResolver);
        assertRejected(() -> grantDenied.service.issue(request(null, List.of("note:write"))));

        verify(missing.pendingStore, never()).save(any(), any());
        verify(inactive.pendingStore, never()).save(any(), any());
        verify(grantDenied.pendingStore, never()).save(any(), any());
    }

    @Test
    void createsPendingForTheRealSessionThenRevalidatesItBeforeTheOnlyCodeTransition() {
        Fixture fixture = fixture();
        when(fixture.transitionStore.consumePendingAndStoreCode(any(), any(), any())).thenReturn(true);

        CoreAgentPreparedPendingAuthorization pending = fixture.service.createPending(
                request(null, List.of("note:write")), "browser-session");

        assertThat(pending.handle().rawHandle()).isEqualTo(RAW_CODE);
        assertThat(pending.state().sessionId()).isEqualTo("browser-session");
        assertThat(pending.state().requestedScopes()).isNull();
        assertThat(pending.toString()).doesNotContain(RAW_CODE, "browser-session", "browser-state");
        IssuedCoreAgentAuthorizationCode issued = fixture.service.convertPending(
                new CoreAgentPendingAuthorizationConversionRequest(pending.handle().rawHandle(), 7L,
                        "browser-session", "core_agent", "http://127.0.0.1:9090/oauth/callback", "browser-state",
                        List.of("note:read", "note:write")));
        assertThat(issued.rawCode()).isEqualTo(RAW_CODE);
        verify(fixture.transitionStore).consumePendingAndStoreCode(eq(pending.handle()), eq(pending.state()), any());

        assertRejected(() -> fixture.service.convertPending(new CoreAgentPendingAuthorizationConversionRequest(
                pending.handle().rawHandle(), 8L, "browser-session", "core_agent",
                "http://127.0.0.1:9090/oauth/callback", "browser-state", List.of("note:read"))));
        assertRejected(() -> fixture.service.convertPending(new CoreAgentPendingAuthorizationConversionRequest(
                pending.handle().rawHandle(), 7L, "other-session", "core_agent",
                "http://127.0.0.1:9090/oauth/callback", "browser-state", List.of("media:read"))));
    }

    @Test
    void failsClosedForRoleMismatchSocketAndClientBinding() {
        Fixture roleMismatch = fixture();
        when(roleMismatch.rolePermissionResolver.resolve(2L)).thenReturn(role(3L));
        assertThatIllegalStateException().isThrownBy(() -> roleMismatch.service.issue(request(null, List.of("note:write"))));

        Fixture socket = fixture();
        assertRejected(() -> socket.service.issue(requestFromSocket("10.0.0.1", List.of("note:write"))));

        Fixture binding = fixture();
        assertRejected(() -> binding.service.issue(new CoreAgentAuthorizationCodeIssueRequest(7L, "other-client",
                "http://127.0.0.1:9090/oauth/callback", null, List.of("note:write"), CHALLENGE, "S256",
                "127.0.0.1", "browser-state")));

        verify(roleMismatch.pendingStore, never()).save(any(), any());
        verify(socket.accountRepository, never()).findById(any());
        verify(binding.accountRepository, never()).findById(any());
    }

    @Test
    void rejectsScopeTamperingAndAnExplicitEmptyScopeSelection() {
        Fixture tampered = fixture();
        assertThatThrownBy(() -> tampered.service.issue(request(null, List.of("media:read"))))
                .isInstanceOf(IllegalArgumentException.class);

        Fixture empty = fixture();
        assertThatThrownBy(() -> empty.service.issue(request(List.of("note:write"), List.of())))
                .isInstanceOf(IllegalArgumentException.class);

        verify(tampered.transitionStore, never()).consumePendingAndStoreCode(any(), any(), any());
        verify(empty.transitionStore, never()).consumePendingAndStoreCode(any(), any(), any());
    }

    @Test
    void rejectsNullGeneratorOutputAndFailsForPendingOrTransitionFailures() {
        Fixture nullGenerator = fixture();
        when(nullGenerator.tokenGenerator.newOpaqueToken()).thenReturn(null);
        assertThatIllegalStateException().isThrownBy(() -> nullGenerator.service.issue(request(null, List.of("note:write"))));

        Fixture generatorFailure = fixture();
        when(generatorFailure.tokenGenerator.newOpaqueToken()).thenThrow(new IllegalStateException("rng down"));
        assertThatIllegalStateException().isThrownBy(() -> generatorFailure.service.issue(request(null, List.of("note:write"))))
                .withMessage("rng down");

        Fixture pendingFailure = fixture();
        doThrow(new IllegalStateException("redis down")).when(pendingFailure.pendingStore).save(any(), any());
        assertThatIllegalStateException().isThrownBy(() -> pendingFailure.service.issue(request(null, List.of("note:write"))))
                .withMessage("redis down");

        Fixture stale = fixture();
        when(stale.transitionStore.consumePendingAndStoreCode(any(), any(), any())).thenReturn(false);
        assertRejected(() -> stale.service.issue(request(null, List.of("note:write"))));
    }

    @Test
    void requestDefensivelyCopiesAndRedactsEveryInput() {
        List<String> requested = new java.util.ArrayList<>(List.of("note:write"));
        List<String> submitted = new java.util.ArrayList<>(List.of("note:write"));
        CoreAgentAuthorizationCodeIssueRequest request = request(requested, submitted);
        requested.add("media:read");
        submitted.add("media:read");

        assertThat(request.requestedScopes()).containsExactly("note:write");
        assertThat(request.submittedOptionalScopes()).containsExactly("note:write");
        assertThatThrownBy(() -> request.requestedScopes().add("media:read"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(request.toString()).doesNotContain("alice", "core_agent", "browser-state", CHALLENGE, "127.0.0.1");
    }

    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOf(AuthenticationException.class)
                .hasMessage(CoreAgentAuthorizationCodeIssueRejectedException.MESSAGE);
    }

    private static Fixture fixture() {
        return fixture(new AccountGrantTypeResolver(AccountGrantTypeResolver.requiredDefaultGrantTypes()));
    }

    private static Fixture fixture(AccountGrantTypeResolver grantTypeResolver) {
        SecureOAuth2TokenGenerator tokenGenerator = mock(SecureOAuth2TokenGenerator.class);
        when(tokenGenerator.newOpaqueToken()).thenReturn(RAW_CODE);
        CoreAgentRegisteredClientPolicyResolver policyResolver = Mockito.mock(CoreAgentRegisteredClientPolicyResolver.class);
        when(policyResolver.resolve(CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID)).thenReturn(policy());
        AuthorizationAccountRepository accountRepository = mock(AuthorizationAccountRepository.class);
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account(UserConstant.ACTIVE_STATUS)));
        EffectiveRolePermissionResolver rolePermissionResolver = Mockito.mock(EffectiveRolePermissionResolver.class);
        when(rolePermissionResolver.resolve(2L)).thenReturn(role(2L));
        CoreAgentPendingAuthorizationStore pendingStore = mock(CoreAgentPendingAuthorizationStore.class);
        CoreAgentPendingAuthorizationCodeTransitionStore transitionStore = mock(CoreAgentPendingAuthorizationCodeTransitionStore.class);
        AtomicReference<CoreAgentPendingAuthorizationState> pending = new AtomicReference<>();
        doAnswer(invocation -> {
            pending.set(invocation.getArgument(1));
            return null;
        }).when(pendingStore).save(any(), any());
        when(pendingStore.find(any())).thenAnswer(invocation -> Optional.ofNullable(pending.get()));
        CoreAgentAuthorizationCodeIssueService service = new CoreAgentAuthorizationCodeIssueService(
                Clock.fixed(NOW, ZoneOffset.UTC), tokenGenerator, policyResolver, accountRepository, grantTypeResolver,
                rolePermissionResolver, new CoreAgentConsentScopeService(new OAuth2ScopeResolver()),
                new CoreAgentPendingAuthorizationHandleGenerator(tokenGenerator), pendingStore, transitionStore);
        return new Fixture(service, tokenGenerator, policyResolver, accountRepository, rolePermissionResolver,
                pendingStore, transitionStore);
    }

    private static CoreAgentAuthorizationCodeIssueRequest request(List<String> requested, List<String> submitted) {
        return request("127.0.0.1", requested, submitted);
    }

    private static CoreAgentAuthorizationCodeIssueRequest requestFromSocket(String socket, List<String> submitted) {
        return request(socket, null, submitted);
    }

    private static CoreAgentAuthorizationCodeIssueRequest request(String socket, List<String> requested,
                                                                  List<String> submitted) {
        return new CoreAgentAuthorizationCodeIssueRequest(7L, "core_agent",
                "http://127.0.0.1:9090/oauth/callback", requested, submitted, CHALLENGE, "S256", socket,
                "browser-state");
    }

    private static CoreAgentRegisteredClientPolicy policy() {
        return new CoreAgentRegisteredClientPolicy("registered-core-agent", "core_agent",
                "http://127.0.0.1:9090/oauth/callback", Set.of("*:read", "note:write"), Set.of("note:read"),
                "127.0.0.1/32", Duration.ofHours(1), Duration.ofHours(24), Duration.ofMinutes(10));
    }

    private static AuthorizationAccount account(int status) {
        return new AuthorizationAccount(7L, "alice", "bcrypt-password", "alice@example.test", 2L,
                "agent_client", status);
    }

    private static EffectiveRolePermissions role(Long roleId) {
        return new EffectiveRolePermissions(roleId, "USER", 2, List.of("*:read", "note:write"));
    }

    private record Fixture(CoreAgentAuthorizationCodeIssueService service,
                           SecureOAuth2TokenGenerator tokenGenerator,
                           CoreAgentRegisteredClientPolicyResolver policyResolver,
                           AuthorizationAccountRepository accountRepository,
                           EffectiveRolePermissionResolver rolePermissionResolver,
                           CoreAgentPendingAuthorizationStore pendingStore,
                           CoreAgentPendingAuthorizationCodeTransitionStore transitionStore) {
    }
}
