package com.jacolp.system.application.authorization;

import com.jacolp.constant.UserConstant;
import com.jacolp.common.core.exception.AuthenticationException;
import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.system.application.port.out.CoreAgentAuthorizationCodeStore;
import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationAccountSnapshot;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationCodeExchangeRequest;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationCodeState;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.VerifiedCoreAgentAuthorizationCode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CoreAgentAuthorizationCodeExchangeServiceTest {

    private static final String RAW_CODE = code((byte) 7);
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String RFC7636_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private static final String REDIRECT_URI = "http://127.0.0.1:9090/oauth/callback";
    private static final long USER_ID = 7L;

    @Test
    void exchangesTheRfc7636VectorThenConsumesExactlyOnce() {
        Fixture fixture = fixture(state(RFC7636_CHALLENGE, snapshot()), account("alice", 2L, hash(), "private@example.test",
                "", UserConstant.ACTIVE_STATUS));
        when(fixture.store.consume(RAW_CODE, USER_ID, "core_agent")).thenReturn(true);

        VerifiedCoreAgentAuthorizationCode verified = fixture.service.exchange(request(VERIFIER, "127.0.0.1"));

        assertThat(verified.registeredClientId()).isEqualTo("registered-core-agent");
        assertThat(verified.clientId()).isEqualTo("core_agent");
        assertThat(verified.userId()).isEqualTo(USER_ID);
        assertThat(verified.consentScopes()).containsExactly("note:read", "sys:read");
        assertThat(verified.grantType()).isEqualTo(AccountGrantTypeResolver.AUTHORIZATION_CODE);
        assertThat(verified.socketAddressChanged()).isFalse();
        verify(fixture.policyResolver).resolve("core_agent");
        verify(fixture.store).findByCode(RAW_CODE);
        verify(fixture.accounts).findById(USER_ID);
        verify(fixture.store).consume(RAW_CODE, USER_ID, "core_agent");
    }

    @Test
    void verifierRequestBoundariesAndRedactionAreStrict() {
        assertThatThrownBy(() -> request("A".repeat(42), "127.0.0.1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request("A".repeat(129), "127.0.0.1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request("A".repeat(42) + "!", "127.0.0.1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request(VERIFIER, "proxy.example.test")).isInstanceOf(IllegalArgumentException.class);
        CoreAgentAuthorizationCodeExchangeRequest request = request(VERIFIER, "127.0.0.1");
        assertThat(request.toString()).contains("<redacted>").doesNotContain(RAW_CODE, VERIFIER, "127.0.0.1", REDIRECT_URI);
        AuthenticationException exception = new CoreAgentAuthorizationCodeExchangeRejectedException();
        assertThat(exception.getMessage()).isEqualTo(CoreAgentAuthorizationCodeExchangeRejectedException.MESSAGE);
    }

    @Test
    void missingClientOrRedirectBindingAndPkceFailureDoNotConsume() {
        Fixture clientMismatch = fixture(state(RFC7636_CHALLENGE, snapshot()), currentAccount());
        assertRejected(() -> clientMismatch.service.exchange(new CoreAgentAuthorizationCodeExchangeRequest(
                RAW_CODE, "other", REDIRECT_URI, VERIFIER, "127.0.0.1")));
        verifyNoInteractions(clientMismatch.store, clientMismatch.accounts);

        Fixture redirectMismatch = fixture(state(RFC7636_CHALLENGE, snapshot()), currentAccount());
        assertRejected(() -> redirectMismatch.service.exchange(new CoreAgentAuthorizationCodeExchangeRequest(
                RAW_CODE, "core_agent", "http://127.0.0.1:9090/other", VERIFIER, "127.0.0.1")));
        verifyNoInteractions(redirectMismatch.store, redirectMismatch.accounts);

        Fixture missing = fixture(null, currentAccount());
        assertRejected(() -> missing.service.exchange(request(VERIFIER, "127.0.0.1")));
        verify(missing.store).findByCode(RAW_CODE);
        verify(missing.store, never()).consume(RAW_CODE, USER_ID, "core_agent");

        Fixture pkce = fixture(state(RFC7636_CHALLENGE, snapshot()), currentAccount());
        assertRejected(() -> pkce.service.exchange(request("A".repeat(43), "127.0.0.1")));
        verify(pkce.store).findByCode(RAW_CODE);
        verify(pkce.store, never()).consume(RAW_CODE, USER_ID, "core_agent");
        Mockito.verifyNoInteractions(pkce.accounts);

        Fixture staleBinding = fixture(stateWithRedirect("http://127.0.0.1:9090/other"), currentAccount());
        assertRejected(() -> staleBinding.service.exchange(request(VERIFIER, "127.0.0.1")));
        verify(staleBinding.store).findByCode(RAW_CODE);
        verify(staleBinding.store, never()).consume(RAW_CODE, USER_ID, "core_agent");
        Mockito.verifyNoInteractions(staleBinding.accounts);

        Fixture wrongCodeIdentity = fixture(stateWithRawCode(code((byte) 11)), currentAccount());
        assertThatIllegalStateException().isThrownBy(() -> wrongCodeIdentity.service.exchange(request(VERIFIER, "127.0.0.1")));
        verify(wrongCodeIdentity.store, never()).consume(RAW_CODE, USER_ID, "core_agent");
    }

    @Test
    void everyMutableSecuritySnapshotFieldInvalidatesOnlyTheOldCodeThenRejects() {
        for (AuthorizationAccount changed : List.of(
                account("bob", 2L, hash(), "private@example.test", "", UserConstant.ACTIVE_STATUS),
                account("alice", 3L, hash(), "private@example.test", "", UserConstant.ACTIVE_STATUS),
                account("alice", 2L, "$2a$10$" + "b".repeat(53), "private@example.test", "", UserConstant.ACTIVE_STATUS),
                account("alice", 2L, hash(), "changed@example.test", "", UserConstant.ACTIVE_STATUS),
                account("alice", 2L, hash(), "private@example.test", "agent_client", UserConstant.ACTIVE_STATUS))) {
            Fixture fixture = fixture(state(RFC7636_CHALLENGE, snapshot()), changed);
            when(fixture.store.consume(RAW_CODE, USER_ID, "core_agent")).thenReturn(true);

            assertRejected(() -> fixture.service.exchange(request(VERIFIER, "127.0.0.1")));
            verify(fixture.store).consume(RAW_CODE, USER_ID, "core_agent");
        }
    }

    @Test
    void missingInactiveAndExtraGrantPollutionFailClosedWithoutAccidentallyInvalidatingNewCode() {
        Fixture missing = fixture(state(RFC7636_CHALLENGE, snapshot()), null);
        assertRejected(() -> missing.service.exchange(request(VERIFIER, "127.0.0.1")));
        verify(missing.store, never()).consume(RAW_CODE, USER_ID, "core_agent");

        Fixture inactive = fixture(state(RFC7636_CHALLENGE, snapshot()), account("alice", 2L, hash(),
                "private@example.test", "", UserConstant.ACTIVE_STATUS + 1));
        assertRejected(() -> inactive.service.exchange(request(VERIFIER, "127.0.0.1")));
        verify(inactive.store, never()).consume(RAW_CODE, USER_ID, "core_agent");

        Fixture polluted = fixture(state(RFC7636_CHALLENGE, snapshot()), account("alice", 2L, hash(),
                "private@example.test", "authorization_code", UserConstant.ACTIVE_STATUS));
        assertThatIllegalStateException().isThrownBy(() -> polluted.service.exchange(request(VERIFIER, "127.0.0.1")));
        verify(polluted.store, never()).consume(RAW_CODE, USER_ID, "core_agent");
    }

    @Test
    void staleConsumeIsAUniformReplayRejectionAndSocketChangeIsOnlyAFlag() {
        Fixture stale = fixture(state(RFC7636_CHALLENGE, snapshot()), currentAccount());
        when(stale.store.consume(RAW_CODE, USER_ID, "core_agent")).thenReturn(false);
        assertRejected(() -> stale.service.exchange(request(VERIFIER, "127.0.0.1")));

        Fixture changedIp = fixture(state(RFC7636_CHALLENGE, snapshot()), currentAccount());
        when(changedIp.store.consume(RAW_CODE, USER_ID, "core_agent")).thenReturn(true);
        assertThat(changedIp.service.exchange(request(VERIFIER, "127.0.0.2")).socketAddressChanged()).isTrue();
    }

    @Test
    void dependencyFailuresAreNotSwallowed() {
        Fixture fixture = fixture(state(RFC7636_CHALLENGE, snapshot()), currentAccount());
        IllegalStateException failure = new IllegalStateException("store unavailable");
        when(fixture.store.findByCode(RAW_CODE)).thenThrow(failure);

        assertThatThrownBy(() -> fixture.service.exchange(request(VERIFIER, "127.0.0.1"))).isSameAs(failure);
    }

    private static void assertRejected(org.junit.jupiter.api.function.Executable executable) {
        assertThatThrownBy(() -> executable.execute()).isInstanceOf(CoreAgentAuthorizationCodeExchangeRejectedException.class)
                .hasMessage(CoreAgentAuthorizationCodeExchangeRejectedException.MESSAGE);
    }

    private static Fixture fixture(CoreAgentAuthorizationCodeState state, AuthorizationAccount account) {
        CoreAgentRegisteredClientPolicyResolver policyResolver = Mockito.mock(CoreAgentRegisteredClientPolicyResolver.class);
        CoreAgentAuthorizationCodeStore store = mock(CoreAgentAuthorizationCodeStore.class);
        AuthorizationAccountRepository accounts = mock(AuthorizationAccountRepository.class);
        when(policyResolver.resolve("core_agent")).thenReturn(policy());
        when(store.findByCode(RAW_CODE)).thenReturn(Optional.ofNullable(state));
        if (state != null) {
            when(accounts.findById(USER_ID)).thenReturn(Optional.ofNullable(account));
        }
        return new Fixture(new CoreAgentAuthorizationCodeExchangeService(policyResolver, store, accounts,
                new AccountGrantTypeResolver(AccountGrantTypeResolver.requiredDefaultGrantTypes())), policyResolver, store, accounts);
    }

    private static CoreAgentRegisteredClientPolicy policy() {
        return new CoreAgentRegisteredClientPolicy("registered-core-agent", "core_agent", REDIRECT_URI,
                Set.of("note:read", "sys:read"), Set.of("note:read"), "0.0.0.0/0", Duration.ofHours(1),
                Duration.ofHours(24), Duration.ofMinutes(10));
    }

    private static CoreAgentAuthorizationCodeState state(String challenge, CoreAgentAuthorizationAccountSnapshot snapshot) {
        Instant issuedAt = Instant.parse("2026-08-11T04:00:00Z");
        return new CoreAgentAuthorizationCodeState(RAW_CODE, "core_agent", REDIRECT_URI, List.of("note:read", "sys:read"),
                challenge, "S256", "127.0.0.1", "opaque-state", issuedAt, issuedAt.plus(Duration.ofMinutes(10)), snapshot);
    }

    private static CoreAgentAuthorizationCodeState stateWithRedirect(String redirectUri) {
        Instant issuedAt = Instant.parse("2026-08-11T04:00:00Z");
        return new CoreAgentAuthorizationCodeState(RAW_CODE, "core_agent", redirectUri, List.of("note:read", "sys:read"),
                RFC7636_CHALLENGE, "S256", "127.0.0.1", "opaque-state", issuedAt, issuedAt.plus(Duration.ofMinutes(10)),
                snapshot());
    }

    private static CoreAgentAuthorizationCodeState stateWithRawCode(String rawCode) {
        Instant issuedAt = Instant.parse("2026-08-11T04:00:00Z");
        return new CoreAgentAuthorizationCodeState(rawCode, "core_agent", REDIRECT_URI, List.of("note:read", "sys:read"),
                RFC7636_CHALLENGE, "S256", "127.0.0.1", "opaque-state", issuedAt, issuedAt.plus(Duration.ofMinutes(10)),
                snapshot());
    }

    private static CoreAgentAuthorizationAccountSnapshot snapshot() {
        return new CoreAgentAuthorizationAccountSnapshot(USER_ID, "alice", 2L, hash(), "private@example.test", "",
                UserConstant.ACTIVE_STATUS);
    }

    private static AuthorizationAccount currentAccount() {
        return account("alice", 2L, hash(), "private@example.test", "", UserConstant.ACTIVE_STATUS);
    }

    private static AuthorizationAccount account(String username, Long roleId, String passwordHash, String email,
                                                String extraGrantTypes, Integer status) {
        return new AuthorizationAccount(USER_ID, username, passwordHash, email, roleId, extraGrantTypes, status);
    }

    private static CoreAgentAuthorizationCodeExchangeRequest request(String verifier, String socketAddress) {
        return new CoreAgentAuthorizationCodeExchangeRequest(RAW_CODE, "core_agent", REDIRECT_URI, verifier, socketAddress);
    }

    private static String hash() {
        return "$2a$10$" + "a".repeat(53);
    }

    private static String code(byte fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, fill);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record Fixture(CoreAgentAuthorizationCodeExchangeService service,
                           CoreAgentRegisteredClientPolicyResolver policyResolver,
                           CoreAgentAuthorizationCodeStore store,
                           AuthorizationAccountRepository accounts) {
    }
}
