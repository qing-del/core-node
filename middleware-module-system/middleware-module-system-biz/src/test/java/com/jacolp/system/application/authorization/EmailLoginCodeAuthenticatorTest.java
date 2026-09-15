package com.jacolp.system.application.authorization;

import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.system.application.port.out.EmailLoginCodeFailureDecision;
import com.jacolp.system.application.port.out.EmailLoginCodeProtector;
import com.jacolp.system.application.port.out.EmailLoginCodeStateStore;
import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.authorization.model.EmailLoginCodeAuthenticationRequest;
import com.jacolp.system.application.authorization.model.EmailLoginCodeState;
import com.jacolp.system.application.authorization.model.InternalAuthenticatedAccount;
import com.jacolp.system.application.authorization.model.InternalRegisteredClientPolicy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailLoginCodeAuthenticatorTest {

    @Test
    void invalidOrNullPolicyAndRequestAreRejectedBeforeDependencies() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.authenticator.authenticate(null, request()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> fixture.authenticator.authenticate(policy("core_agent", "email-code"), request()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> fixture.authenticator.authenticate(policy("user", "password"), request()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> fixture.authenticator.authenticate(fixture.policy, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EmailLoginCodeAuthenticationRequest("alice@example.test", "bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailLoginCodeAuthenticationRequest("alice example.test", "012345"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(fixture.accounts, never()).findByEmail(anyString());
        verify(fixture.states, never()).find(anyString(), anyLong());
    }

    @Test
    void authenticatesInOrderMatchesThenConsumesThenChecksEligibility() {
        Fixture fixture = preparedFixture();
        when(fixture.protector.matches("012345", fixture.state.verifierHash())).thenReturn(true);
        when(fixture.states.consume("user", 7L, fixture.state.verifierHash())).thenReturn(true);
        when(fixture.eligibility.resolve(eq(fixture.policy), eq(fixture.account))).thenReturn(fixture.cleared);

        Assertions.assertThat(fixture.authenticator.authenticate(fixture.policy, request())).isEqualTo(fixture.cleared);

        InOrder order = inOrder(fixture.protector, fixture.states, fixture.eligibility);
        order.verify(fixture.protector).matches("012345", fixture.state.verifierHash());
        order.verify(fixture.states).consume("user", 7L, fixture.state.verifierHash());
        order.verify(fixture.eligibility).resolve(fixture.policy, fixture.account);
    }

    @Test
    void nullOrMissingAccountAndAccountEmailMismatchUseExactlyOneDummyMatch() {
        Fixture nullRepositoryResult = fixture();
        when(nullRepositoryResult.accounts.findByEmail("alice@example.test")).thenReturn(null);
        assertThatThrownBy(() -> nullRepositoryResult.authenticator.authenticate(nullRepositoryResult.policy, request()))
                .isInstanceOf(IllegalStateException.class);
        verify(nullRepositoryResult.protector, never()).matches(anyString(), anyString());

        Fixture missing = fixture();
        when(missing.accounts.findByEmail("alice@example.test")).thenReturn(Optional.empty());
        assertRejected(missing);
        verify(missing.protector).matches("012345", null);
        verify(missing.states, never()).find(anyString(), anyLong());

        Fixture nullEmail = fixture();
        AuthorizationAccount noEmail = new AuthorizationAccount(7L, "alice", "$2a$10$" + "b".repeat(53),
                null, 3L, "", 1);
        when(nullEmail.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(noEmail));
        assertRejected(nullEmail);
        verify(nullEmail.protector).matches("012345", null);
        verify(nullEmail.states, never()).find(anyString(), anyLong());

        Fixture mismatch = fixture();
        AuthorizationAccount wrongEmail = new AuthorizationAccount(7L, "alice", "$2a$10$" + "b".repeat(53),
                "other@example.test", 3L, "", 1);
        when(mismatch.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(wrongEmail));
        assertRejected(mismatch);
        verify(mismatch.protector).matches("012345", null);
        verify(mismatch.states, never()).find(anyString(), anyLong());
    }

    @Test
    void nullOrMissingStateAndFingerprintMismatchUseExactlyOneDummyMatch() {
        Fixture nullState = preparedAccountFixture();
        when(nullState.states.find("user", 7L)).thenReturn(null);
        assertThatThrownBy(() -> nullState.authenticator.authenticate(nullState.policy, request()))
                .isInstanceOf(IllegalStateException.class);
        verify(nullState.protector, never()).matches(anyString(), anyString());

        Fixture missing = preparedAccountFixture();
        when(missing.states.find("user", 7L)).thenReturn(Optional.empty());
        assertRejected(missing);
        verify(missing.protector).matches("012345", null);

        Fixture mismatch = preparedAccountFixture();
        EmailLoginCodeState foreignState = state("other@example.test", Instant.EPOCH,
                Instant.EPOCH.plus(Duration.ofMinutes(10)));
        when(mismatch.states.find("user", 7L)).thenReturn(Optional.of(foreignState));
        assertRejected(mismatch);
        verify(mismatch.protector).matches("012345", null);
    }

    @Test
    void wrongCodeAllFailureDecisionsRejectWithoutConsumeOrEligibility() {
        for (EmailLoginCodeFailureDecision decision : EmailLoginCodeFailureDecision.values()) {
            Fixture fixture = preparedFixture();
            when(fixture.protector.matches("012345", fixture.state.verifierHash())).thenReturn(false);
            when(fixture.states.recordFailure("user", 7L, fixture.state.verifierHash(), 5)).thenReturn(decision);

            assertRejected(fixture);
            verify(fixture.states).recordFailure("user", 7L, fixture.state.verifierHash(), 5);
            verify(fixture.states, never()).consume(anyString(), anyLong(), anyString());
            verify(fixture.eligibility, never()).resolve(any(), any());
        }
    }

    @Test
    void nullFailureDecisionFailsClosedWithoutConsumeOrEligibility() {
        Fixture fixture = preparedFixture();
        when(fixture.protector.matches("012345", fixture.state.verifierHash())).thenReturn(false);
        when(fixture.states.recordFailure("user", 7L, fixture.state.verifierHash(), 5)).thenReturn(null);

        assertThatThrownBy(() -> fixture.authenticator.authenticate(fixture.policy, request()))
                .isInstanceOf(IllegalStateException.class);
        verify(fixture.states, never()).consume(anyString(), anyLong(), anyString());
        verify(fixture.eligibility, never()).resolve(any(), any());
    }

    @Test
    void consumeFalseRejectsAndDoesNotCheckEligibility() {
        Fixture fixture = preparedFixture();
        when(fixture.protector.matches("012345", fixture.state.verifierHash())).thenReturn(true);
        when(fixture.states.consume("user", 7L, fixture.state.verifierHash())).thenReturn(false);

        assertRejected(fixture);
        verify(fixture.eligibility, never()).resolve(any(), any());
    }

    @Test
    void eligibilityRejectionReasonIsPreservedAfterConsumption() {
        Fixture fixture = preparedFixture();
        when(fixture.protector.matches("012345", fixture.state.verifierHash())).thenReturn(true);
        when(fixture.states.consume("user", 7L, fixture.state.verifierHash())).thenReturn(true);
        when(fixture.eligibility.resolve(fixture.policy, fixture.account))
                .thenThrow(new InternalAccountAuthenticationRejectedException(
                        InternalAccountAuthenticationRejectedException.Reason.ROLE_NOT_ALLOWED));

        assertThatThrownBy(() -> fixture.authenticator.authenticate(fixture.policy, request()))
                .isInstanceOf(InternalAccountAuthenticationRejectedException.class)
                .hasMessage("当前账号角色不允许使用该登录客户端");
    }

    @Test
    void eligibilityMetadataAnomaliesFailClosedAfterConsumption() {
        for (InternalAuthenticatedAccount cleared : new InternalAuthenticatedAccount[]{
                null,
                new InternalAuthenticatedAccount(8L, "alice", "alice@example.test", 3L, "USER", 3),
                new InternalAuthenticatedAccount(7L, "alice", "other@example.test", 3L, "USER", 3)}) {
            Fixture fixture = preparedFixture();
            when(fixture.protector.matches("012345", fixture.state.verifierHash())).thenReturn(true);
            when(fixture.states.consume("user", 7L, fixture.state.verifierHash())).thenReturn(true);
            when(fixture.eligibility.resolve(fixture.policy, fixture.account)).thenReturn(cleared);

            assertThatThrownBy(() -> fixture.authenticator.authenticate(fixture.policy, request()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void dependencyExceptionsPropagateWithoutFallbackOrExtraMutation() {
        Fixture repositoryFailure = fixture();
        RuntimeException repositoryException = new RuntimeException("repository");
        when(repositoryFailure.accounts.findByEmail("alice@example.test")).thenThrow(repositoryException);
        assertThatThrownBy(() -> repositoryFailure.authenticator.authenticate(repositoryFailure.policy, request()))
                .isSameAs(repositoryException);

        Fixture stateFailure = preparedAccountFixture();
        RuntimeException stateException = new RuntimeException("state");
        when(stateFailure.states.find("user", 7L)).thenThrow(stateException);
        assertThatThrownBy(() -> stateFailure.authenticator.authenticate(stateFailure.policy, request()))
                .isSameAs(stateException);

        Fixture protectorFailure = preparedFixture();
        RuntimeException protectorException = new RuntimeException("protector");
        when(protectorFailure.protector.matches("012345", protectorFailure.state.verifierHash()))
                .thenThrow(protectorException);
        assertThatThrownBy(() -> protectorFailure.authenticator.authenticate(protectorFailure.policy, request()))
                .isSameAs(protectorException);

        Fixture recordFailure = preparedFixture();
        RuntimeException recordException = new RuntimeException("record");
        when(recordFailure.protector.matches("012345", recordFailure.state.verifierHash())).thenReturn(false);
        when(recordFailure.states.recordFailure("user", 7L, recordFailure.state.verifierHash(), 5))
                .thenThrow(recordException);
        assertThatThrownBy(() -> recordFailure.authenticator.authenticate(recordFailure.policy, request()))
                .isSameAs(recordException);

        Fixture consumeFailure = preparedFixture();
        RuntimeException consumeException = new RuntimeException("consume");
        when(consumeFailure.protector.matches("012345", consumeFailure.state.verifierHash())).thenReturn(true);
        when(consumeFailure.states.consume("user", 7L, consumeFailure.state.verifierHash()))
                .thenThrow(consumeException);
        assertThatThrownBy(() -> consumeFailure.authenticator.authenticate(consumeFailure.policy, request()))
                .isSameAs(consumeException);
    }

    @Test
    void futureAndExpiredStatesDeleteWithoutFailureOrConsumption() {
        for (EmailLoginCodeState state : new EmailLoginCodeState[]{
                state("alice@example.test", Instant.EPOCH.plusSeconds(1), Instant.EPOCH.plus(Duration.ofMinutes(10))),
                state("alice@example.test", Instant.EPOCH.minus(Duration.ofMinutes(10)), Instant.EPOCH)}) {
            Fixture fixture = preparedAccountFixture();
            when(fixture.states.find("user", 7L)).thenReturn(Optional.of(state));
            when(fixture.protector.matches("012345", state.verifierHash())).thenReturn(true);

            assertThatThrownBy(() -> fixture.authenticator.authenticate(fixture.policy, request()))
                    .isInstanceOf(RuntimeException.class);
            verify(fixture.protector).matches("012345", state.verifierHash());
            verify(fixture.states).delete("user", 7L);
            verify(fixture.states, never()).recordFailure(any(), any(), any(), any());
            verify(fixture.states, never()).consume(any(), any(), any());
        }
    }

    @Test
    void deleteExceptionOnFutureOrExpiredStatePropagates() {
        for (EmailLoginCodeState state : new EmailLoginCodeState[]{
                state("alice@example.test", Instant.EPOCH.plusSeconds(1), Instant.EPOCH.plus(Duration.ofMinutes(10))),
                state("alice@example.test", Instant.EPOCH.minus(Duration.ofMinutes(10)), Instant.EPOCH)}) {
            Fixture fixture = preparedAccountFixture();
            RuntimeException deleteException = new RuntimeException("delete");
            when(fixture.states.find("user", 7L)).thenReturn(Optional.of(state));
            when(fixture.protector.matches("012345", state.verifierHash())).thenReturn(true);
            doThrow(deleteException).when(fixture.states).delete("user", 7L);

            assertThatThrownBy(() -> fixture.authenticator.authenticate(fixture.policy, request()))
                    .isSameAs(deleteException);
        }
    }

    @Test
    void redactedValuesDoNotExposeRawCodeOrVerifier() {
        Fixture fixture = fixture();
        assertThat(request().toString()).doesNotContain("012345");
        assertThat(fixture.state.toString()).doesNotContain("012345").doesNotContain(fixture.state.verifierHash());
    }

    private static void assertRejected(Fixture fixture) {
        assertThatThrownBy(() -> fixture.authenticator.authenticate(fixture.policy, request()))
                .isInstanceOf(InternalAccountAuthenticationRejectedException.class)
                .hasMessage("邮箱验证码错误或已过期");
    }

    private static EmailLoginCodeAuthenticationRequest request() {
        return new EmailLoginCodeAuthenticationRequest("alice@example.test", "012345");
    }

    private static Fixture preparedFixture() {
        Fixture fixture = preparedAccountFixture();
        when(fixture.states.find("user", 7L)).thenReturn(Optional.of(fixture.state));
        return fixture;
    }

    private static Fixture preparedAccountFixture() {
        Fixture fixture = fixture();
        when(fixture.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(fixture.account));
        return fixture;
    }

    private static Fixture fixture() {
        AuthorizationAccountRepository accounts = mock(AuthorizationAccountRepository.class);
        EmailLoginCodeProtector protector = mock(EmailLoginCodeProtector.class);
        EmailLoginCodeStateStore states = mock(EmailLoginCodeStateStore.class);
        InternalAccountEligibilityService eligibility = Mockito.mock(InternalAccountEligibilityService.class);
        OAuth2EmailLoginCodeProperties properties = new OAuth2EmailLoginCodeProperties();
        InternalRegisteredClientPolicy policy = policy("user", "email-code");
        AuthorizationAccount account = new AuthorizationAccount(7L, "alice", "$2a$10$" + "b".repeat(53),
                "alice@example.test", 3L, "", 1);
        EmailLoginCodeState state = state("alice@example.test", Instant.EPOCH,
                Instant.EPOCH.plus(Duration.ofMinutes(10)));
        InternalAuthenticatedAccount cleared = new InternalAuthenticatedAccount(7L, "alice",
                "alice@example.test", 3L, "USER", 3);
        return new Fixture(new EmailLoginCodeAuthenticator(accounts, protector, states, eligibility,
                new EmailLoginBindingFingerprint(), properties, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
                accounts, protector, states, eligibility, policy, account, state, cleared);
    }

    private static InternalRegisteredClientPolicy policy(String clientId, String grantType) {
        return new InternalRegisteredClientPolicy("id", clientId, grantType,
                Set.of("note:read"), Set.of("note:read"), "192.0.2.0/24", Duration.ofHours(3), Duration.ofHours(72));
    }

    private static EmailLoginCodeState state(String email, Instant issuedAt, Instant expiresAt) {
        String fingerprint = new EmailLoginBindingFingerprint().email(email);
        return new EmailLoginCodeState("user", 7L, fingerprint, "$2a$10$" + "a".repeat(53), 0,
                issuedAt, expiresAt);
    }

    private record Fixture(EmailLoginCodeAuthenticator authenticator, AuthorizationAccountRepository accounts,
                           EmailLoginCodeProtector protector, EmailLoginCodeStateStore states,
                           InternalAccountEligibilityService eligibility, InternalRegisteredClientPolicy policy,
                           AuthorizationAccount account, EmailLoginCodeState state,
                           InternalAuthenticatedAccount cleared) {
    }
}
