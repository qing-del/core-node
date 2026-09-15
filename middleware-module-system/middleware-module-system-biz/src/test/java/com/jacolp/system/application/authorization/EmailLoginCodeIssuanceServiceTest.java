package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.EmailLoginCodeDeliveryRequest;
import com.jacolp.system.application.authorization.model.EmailLoginCodeIssueRequest;
import com.jacolp.system.application.authorization.model.EmailLoginCodeIssueRateLimitRequest;
import com.jacolp.system.application.authorization.model.EmailLoginCodeState;
import com.jacolp.system.application.authorization.model.InternalAuthenticatedAccount;
import com.jacolp.system.application.authorization.model.InternalRegisteredClientPolicy;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.system.application.port.out.EmailLoginCodeDeliveryPort;
import com.jacolp.system.application.port.out.EmailLoginCodeGenerator;
import com.jacolp.system.application.port.out.EmailLoginCodeIssueRateLimitDecision;
import com.jacolp.system.application.port.out.EmailLoginCodeIssueRateLimiter;
import com.jacolp.system.application.port.out.EmailLoginCodeProtector;
import com.jacolp.system.application.port.out.EmailLoginCodeStateStore;
import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.Clock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

class EmailLoginCodeIssuanceServiceTest {

    @Test
    void rateDeniedDoesNotLookupOrGenerate() {
        Fixture fixture = fixture(EmailLoginCodeIssueRateLimitDecision.COOLDOWN);

        fixture.service.issue(request());

        verify(fixture.accounts, never()).findByEmail(any());
        verify(fixture.generator, never()).generate();
        verify(fixture.protector, never()).protect(any());
    }

    @Test
    void windowLimitAlsoDoesNotLookupOrGenerateAndNullDecisionFailsClosed() {
        Fixture limited = fixture(EmailLoginCodeIssueRateLimitDecision.WINDOW_LIMIT);
        limited.service.issue(request());
        verify(limited.accounts, never()).findByEmail(any());
        verify(limited.generator, never()).generate();

        Fixture malformed = fixture(null);
        assertThatThrownBy(() -> malformed.service.issue(request())).isInstanceOf(IllegalStateException.class);
        verify(malformed.accounts, never()).findByEmail(any());
    }

    @Test
    void missingAccountThrowsReasonedRejectionWithoutStoringOrDelivering() {
        Fixture fixture = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        when(fixture.accounts.findByEmail("alice@example.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.service.issue(request()))
                .isInstanceOf(InternalAccountAuthenticationRejectedException.class)
                .satisfies(error -> {
                    InternalAccountAuthenticationRejectedException rejected =
                            (InternalAccountAuthenticationRejectedException) error;
                    assertThat(rejected.reason()).isEqualTo(
                            InternalAccountAuthenticationRejectedException.Reason.ACCOUNT_NOT_FOUND);
                    assertThat(rejected.getMessage()).isEqualTo("邮箱未注册，无法发送验证码");
                    assertThat(rejected.getLogMessage()).isEqualTo("Internal email-code account was not found");
                });

        verify(fixture.generator).generate();
        verify(fixture.protector).protect("012345");
        verify(fixture.states, never()).replace(any());
        verify(fixture.delivery, never()).deliver(any());
    }

    @Test
    void smtpFailureDeletesStateAndRethrows() {
        Fixture fixture = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        when(fixture.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(fixture.account));
        IllegalStateException failure = new IllegalStateException("smtp");
        org.mockito.Mockito.doThrow(failure).when(fixture.delivery).deliver(any());

        assertThatThrownBy(() -> fixture.service.issue(request())).isSameAs(failure);
        verify(fixture.states).replace(any());
        verify(fixture.states).delete("user", 7L);
    }

    @Test
    void replaceFailureDoesNotDeliverOrDeleteAndCleanupFailureSuppressesSmtpFailure() {
        Fixture replaceFailure = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        when(replaceFailure.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(replaceFailure.account));
        IllegalStateException stateFailure = new IllegalStateException("state");
        org.mockito.Mockito.doThrow(stateFailure).when(replaceFailure.states).replace(any());
        assertThatThrownBy(() -> replaceFailure.service.issue(request())).isSameAs(stateFailure);
        verify(replaceFailure.delivery, never()).deliver(any());
        verify(replaceFailure.states, never()).delete(any(), any());

        Fixture cleanupFailure = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        when(cleanupFailure.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(cleanupFailure.account));
        IllegalStateException smtpFailure = new IllegalStateException("smtp");
        IllegalStateException deleteFailure = new IllegalStateException("delete");
        org.mockito.Mockito.doThrow(smtpFailure).when(cleanupFailure.delivery).deliver(any());
        org.mockito.Mockito.doThrow(deleteFailure).when(cleanupFailure.states).delete("user", 7L);
        assertThatThrownBy(() -> cleanupFailure.service.issue(request()))
                .isSameAs(deleteFailure)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(error.getSuppressed()).containsExactly(smtpFailure));
    }

    @Test
    void ipAndResolverRejectionsStopBeforeRateAndLookup() {
        Fixture ipDenied = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        InternalRegisteredClientPolicy deniedPolicy = new InternalRegisteredClientPolicy("id", "user", "email-code",
                Set.of("note:read"), Set.of("note:read"), "198.51.100.0/24", Duration.ofHours(3), Duration.ofHours(72));
        when(ipDenied.resolver.resolve("user", "email-code")).thenReturn(deniedPolicy);
        assertThatThrownBy(() -> ipDenied.service.issue(request())).isInstanceOf(EmailLoginCodeIssuanceRejectedException.class);
        verify(ipDenied.limiter, never()).tryAcquire(any());
        verify(ipDenied.generator, never()).generate();

        Fixture resolverFailure = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        IllegalStateException failure = new IllegalStateException("resolver");
        when(resolverFailure.resolver.resolve("user", "email-code")).thenThrow(failure);
        assertThatThrownBy(() -> resolverFailure.service.issue(request())).isSameAs(failure);
        verify(resolverFailure.limiter, never()).tryAcquire(any());
    }

    @Test
    void emailMismatchThrowsReasonedRejectionWithoutStoringOrDelivering() {
        Fixture mismatch = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        AuthorizationAccount wrongEmail =
                new AuthorizationAccount(
                        7L, "alice", "$2a$10$" + "b".repeat(53), "other@example.test", 3L, "", 1);
        when(mismatch.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(wrongEmail));
        assertThatThrownBy(() -> mismatch.service.issue(request()))
                .isInstanceOf(InternalAccountAuthenticationRejectedException.class)
                .satisfies(error -> assertThat(((InternalAccountAuthenticationRejectedException) error).reason())
                        .isEqualTo(InternalAccountAuthenticationRejectedException.Reason.EMAIL_MISMATCH));
        verify(mismatch.generator).generate();
        verify(mismatch.protector).protect("012345");
        verify(mismatch.eligibility, never()).resolve(any(), any());
        verify(mismatch.states, never()).replace(any());
        verify(mismatch.delivery, never()).deliver(any());
    }

    @Test
    void eligibilityRejectionsPropagateTheirReasonsWithoutStoringOrDelivering() {
        for (InternalAccountAuthenticationRejectedException.Reason reason : new InternalAccountAuthenticationRejectedException.Reason[]{
                InternalAccountAuthenticationRejectedException.Reason.ACCOUNT_NOT_ACTIVATED,
                InternalAccountAuthenticationRejectedException.Reason.ACCOUNT_DISABLED,
                InternalAccountAuthenticationRejectedException.Reason.ROLE_NOT_ALLOWED,
                InternalAccountAuthenticationRejectedException.Reason.GRANT_TYPE_NOT_ALLOWED}) {
            Fixture rejected = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
            when(rejected.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(rejected.account));
            InternalAccountAuthenticationRejectedException failure =
                    new InternalAccountAuthenticationRejectedException(reason);
            when(rejected.eligibility.resolve(any(), any())).thenThrow(failure);

            assertThatThrownBy(() -> rejected.service.issue(request())).isSameAs(failure);
            verify(rejected.states, never()).replace(any());
            verify(rejected.delivery, never()).deliver(any());
        }
    }

    @Test
    void nullLookupAndNullAccountEmailFailClosedAfterProtecting() {
        Fixture nullLookup = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        when(nullLookup.accounts.findByEmail("alice@example.test")).thenReturn(null);
        assertThatThrownBy(() -> nullLookup.service.issue(request())).isInstanceOf(IllegalStateException.class);
        verify(nullLookup.generator).generate();
        verify(nullLookup.protector).protect("012345");
        verify(nullLookup.states, never()).replace(any());

        Fixture nullEmail = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        AuthorizationAccount account =
                new AuthorizationAccount(
                        7L, "alice", "$2a$10$" + "b".repeat(53), null, 3L, "", 1);
        when(nullEmail.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> nullEmail.service.issue(request()))
                .isInstanceOf(InternalAccountAuthenticationRejectedException.class)
                .satisfies(error -> assertThat(((InternalAccountAuthenticationRejectedException) error).reason())
                        .isEqualTo(InternalAccountAuthenticationRejectedException.Reason.EMAIL_MISMATCH));
        verify(nullEmail.eligibility, never()).resolve(any(), any());
        verify(nullEmail.states, never()).replace(any());
        verify(nullEmail.delivery, never()).deliver(any());
    }

    @Test
    void invalidClearedIdentitiesFailClosedWithoutStateOrDelivery() {
        for (InternalAuthenticatedAccount cleared :
                new InternalAuthenticatedAccount[]{
                        null,
                        new InternalAuthenticatedAccount(
                                8L, "alice", "alice@example.test", 3L, "USER", 3),
                        new InternalAuthenticatedAccount(
                                7L, "alice", "other@example.test", 3L, "USER", 3)}) {
            Fixture fixture = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
            when(fixture.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(fixture.account));
            when(fixture.eligibility.resolve(any(), any())).thenReturn(cleared);
            assertThatThrownBy(() -> fixture.service.issue(request())).isInstanceOf(IllegalStateException.class);
            verify(fixture.states, never()).replace(any());
            verify(fixture.delivery, never()).deliver(any());
        }
    }

    @Test
    void dependencyFailuresPropagateWithoutLaterSideEffectsAndDeliveryErrorCleansUp() {
        Fixture limiterFailure = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        IllegalStateException limiterError = new IllegalStateException("limiter");
        when(limiterFailure.limiter.tryAcquire(any())).thenThrow(limiterError);
        assertThatThrownBy(() -> limiterFailure.service.issue(request())).isSameAs(limiterError);
        verify(limiterFailure.generator, never()).generate();

        Fixture eligibilityFailure = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        when(eligibilityFailure.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(eligibilityFailure.account));
        IllegalStateException eligibilityError = new IllegalStateException("eligibility");
        when(eligibilityFailure.eligibility.resolve(any(), any())).thenThrow(eligibilityError);
        assertThatThrownBy(() -> eligibilityFailure.service.issue(request())).isSameAs(eligibilityError);
        verify(eligibilityFailure.states, never()).replace(any());

        Fixture errorDelivery = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        when(errorDelivery.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(errorDelivery.account));
        AssertionError error = new AssertionError("smtp");
        org.mockito.Mockito.doThrow(error).when(errorDelivery.delivery).deliver(any());
        assertThatThrownBy(() -> errorDelivery.service.issue(request())).isSameAs(error);
        verify(errorDelivery.states).delete("user", 7L);
    }

    @Test
    void validIssuePersistsAndDeliversTheClearedIdentityInOrder() {
        Fixture fixture = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        when(fixture.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(fixture.account));
        InternalAuthenticatedAccount cleared = new InternalAuthenticatedAccount(
                7L, "Cleared", "Alice@Example.Test", 3L, "USER", 3);
        when(fixture.eligibility.resolve(any(), any())).thenReturn(cleared);
        ArgumentCaptor<EmailLoginCodeState> state = ArgumentCaptor.forClass(EmailLoginCodeState.class);
        ArgumentCaptor<EmailLoginCodeIssueRateLimitRequest> rateRequest =
                ArgumentCaptor.forClass(EmailLoginCodeIssueRateLimitRequest.class);
        ArgumentCaptor<EmailLoginCodeDeliveryRequest> delivery =
                ArgumentCaptor.forClass(EmailLoginCodeDeliveryRequest.class);

        fixture.service.issue(request());

        verify(fixture.states).replace(state.capture());
        verify(fixture.limiter).tryAcquire(rateRequest.capture());
        verify(fixture.delivery).deliver(delivery.capture());
        assertThat(state.getValue().userId()).isEqualTo(7L);
        assertThat(state.getValue().failedAttempts()).isZero();
        assertThat(state.getValue().issuedAt()).isEqualTo(Instant.EPOCH);
        assertThat(state.getValue().expiresAt()).isEqualTo(Instant.EPOCH.plus(Duration.ofMinutes(10)));
        assertThat(state.getValue().toString()).doesNotContain("alice", "012345", "$2a$");
        assertThat(delivery.getValue().email()).isEqualTo("Alice@Example.Test");
        assertThat(delivery.getValue().username()).isEqualTo("Cleared");
        assertThat(delivery.getValue().rawCode()).isEqualTo("012345");
        assertThat(delivery.getValue().ttl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(rateRequest.getValue().emailFingerprint()).hasSize(43).doesNotContain("alice", "192.0.2.1");
        assertThat(rateRequest.getValue().ipFingerprint()).hasSize(43).doesNotContain("alice", "192.0.2.1");
        assertThat(rateRequest.getValue().emailFingerprint()).isNotEqualTo(rateRequest.getValue().ipFingerprint());
        assertThat(rateRequest.getValue().cooldown()).isEqualTo(Duration.ofSeconds(60));
        assertThat(rateRequest.getValue().window()).isEqualTo(Duration.ofHours(1));
        assertThat(rateRequest.getValue().maxIssues()).isEqualTo(5);
        InOrder order = inOrder(fixture.resolver, fixture.limiter, fixture.generator, fixture.protector,
                fixture.accounts, fixture.eligibility, fixture.states, fixture.delivery);
        order.verify(fixture.resolver).resolve("user", "email-code");
        order.verify(fixture.limiter).tryAcquire(any());
        order.verify(fixture.generator).generate();
        order.verify(fixture.protector).protect("012345");
        order.verify(fixture.accounts).findByEmail("alice@example.test");
        order.verify(fixture.eligibility).resolve(any(), any());
        order.verify(fixture.states).replace(any());
        order.verify(fixture.delivery).deliver(any());
    }

    @Test
    void generatorProtectorAndLookupFailuresPropagateWithoutLaterEffects() {
        Fixture generatorFailure = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        IllegalStateException generatorError = new IllegalStateException("generator");
        when(generatorFailure.generator.generate()).thenThrow(generatorError);
        assertThatThrownBy(() -> generatorFailure.service.issue(request())).isSameAs(generatorError);
        verify(generatorFailure.protector, never()).protect(any());
        verify(generatorFailure.accounts, never()).findByEmail(any());

        Fixture protectorFailure = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        IllegalStateException protectorError = new IllegalStateException("protector");
        when(protectorFailure.protector.protect("012345")).thenThrow(protectorError);
        assertThatThrownBy(() -> protectorFailure.service.issue(request())).isSameAs(protectorError);
        verify(protectorFailure.accounts, never()).findByEmail(any());

        Fixture lookupFailure = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        IllegalStateException lookupError = new IllegalStateException("lookup");
        when(lookupFailure.accounts.findByEmail("alice@example.test")).thenThrow(lookupError);
        assertThatThrownBy(() -> lookupFailure.service.issue(request())).isSameAs(lookupError);
        verify(lookupFailure.states, never()).replace(any());
        verify(lookupFailure.delivery, never()).deliver(any());
    }

    @Test
    void productionServiceDoesNotReferenceLegacyOrOutboxDeliveryPaths() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/jacolp/system/application/authorization/EmailLoginCodeIssuanceService.java"));
        assertThat(source).doesNotContain("Outbox", "EmailSendEventPublisher", "EmailSenderService",
                "TokenSessionService", "activation", "email-change", "Logger", "log.");
    }


    private static EmailLoginCodeIssueRequest request() {
        return new EmailLoginCodeIssueRequest("user", "alice@example.test", "192.0.2.1");
    }

    private static Fixture fixture(EmailLoginCodeIssueRateLimitDecision decision) {
        InternalRegisteredClientPolicyResolver resolver = Mockito.mock(InternalRegisteredClientPolicyResolver.class);
        EmailLoginBindingFingerprint fingerprint = new EmailLoginBindingFingerprint();
        EmailLoginCodeIssueRateLimiter limiter = mock(EmailLoginCodeIssueRateLimiter.class);
        AuthorizationAccountRepository accounts = mock(AuthorizationAccountRepository.class);
        EmailLoginCodeGenerator generator = mock(EmailLoginCodeGenerator.class);
        EmailLoginCodeProtector protector = mock(EmailLoginCodeProtector.class);
        InternalAccountEligibilityService eligibility = Mockito.mock(InternalAccountEligibilityService.class);
        EmailLoginCodeStateStore states = mock(EmailLoginCodeStateStore.class);
        EmailLoginCodeDeliveryPort delivery = mock(EmailLoginCodeDeliveryPort.class);
        OAuth2EmailLoginCodeProperties properties = new OAuth2EmailLoginCodeProperties();
        InternalRegisteredClientPolicy policy = new InternalRegisteredClientPolicy("id", "user", "email-code",
                Set.of("note:read"), Set.of("note:read"), "192.0.2.0/24", Duration.ofHours(3), Duration.ofHours(72));
        when(resolver.resolve("user", "email-code")).thenReturn(policy);
        when(limiter.tryAcquire(any())).thenReturn(decision);
        when(generator.generate()).thenReturn("012345");
        when(protector.protect("012345")).thenReturn("$2a$10$" + "a".repeat(53));
        AuthorizationAccount account =
                new AuthorizationAccount(
                        7L, "alice", "$2a$10$" + "b".repeat(53), "alice@example.test", 3L, "", 1);
        when(eligibility.resolve(eq(policy), eq(account))).thenReturn(
                new InternalAuthenticatedAccount(
                        7L, "alice", "alice@example.test", 3L, "USER", 3));
        return new Fixture(new EmailLoginCodeIssuanceService(resolver, fingerprint, limiter, accounts, generator,
                protector, eligibility, states, delivery, properties, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
                resolver, limiter, accounts, generator, protector, eligibility, states, delivery, account);
    }

    private record Fixture(EmailLoginCodeIssuanceService service, InternalRegisteredClientPolicyResolver resolver,
                           EmailLoginCodeIssueRateLimiter limiter, AuthorizationAccountRepository accounts,
                           EmailLoginCodeGenerator generator, EmailLoginCodeProtector protector,
                           InternalAccountEligibilityService eligibility, EmailLoginCodeStateStore states,
                           EmailLoginCodeDeliveryPort delivery,
                           AuthorizationAccount account) {
    }
}
