package com.jacolp.system.application.authorization;

import com.jacolp.system.exception.PasswordIncorrectException;
import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.authorization.model.InternalAuthenticatedAccount;
import com.jacolp.system.application.authorization.model.InternalRegisteredClientPolicy;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.system.application.port.out.PasswordCredentialVerifier;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InternalPasswordAccountAuthenticatorTest {

    @Test
    void authenticatesOnlyAfterLookingUpTheAccountThenVerifyingItsRawPassword() {
        Fixture fixture = fixture();
        AuthorizationAccount account = account();
        InternalAuthenticatedAccount authenticated = authenticatedAccount();
        when(fixture.accounts.findByUsername("alice")).thenReturn(Optional.of(account));
        when(fixture.credentials.matches("raw password", "stored-password-hash")).thenReturn(true);
        when(fixture.eligibility.resolve(policy(), account)).thenReturn(authenticated);

        Assertions.assertThat(fixture.authenticator.authenticate(policy(), "alice", "raw password")).isSameAs(authenticated);

        InOrder order = inOrder(fixture.accounts, fixture.credentials, fixture.eligibility);
        order.verify(fixture.accounts).findByUsername("alice");
        order.verify(fixture.credentials).matches("raw password", "stored-password-hash");
        order.verify(fixture.eligibility).resolve(policy(), account);
    }

    @Test
    void missingAccountAndWrongPasswordShareOneChinesePasswordMessageButHaveDistinctEnglishLogReasons() {
        Fixture missing = fixture();
        when(missing.accounts.findByUsername("alice")).thenReturn(Optional.empty());
        when(missing.credentials.matches("raw password", null)).thenReturn(false);

        Throwable missingFailure = catchThrowable(() -> missing.authenticator.authenticate(
                policy(), "alice", "raw password"));
        assertPasswordRejected(missingFailure, "Internal login account was not found");
        verify(missing.accounts).findByUsername("alice");
        verify(missing.credentials).matches("raw password", null);
        verifyNoInteractions(missing.eligibility);

        Fixture wrongPassword = fixture();
        AuthorizationAccount account = account();
        when(wrongPassword.accounts.findByUsername("alice")).thenReturn(Optional.of(account));
        when(wrongPassword.credentials.matches("raw password", "stored-password-hash")).thenReturn(false);

        Throwable wrongPasswordFailure = catchThrowable(() -> wrongPassword.authenticator.authenticate(
                policy(), "alice", "raw password"));
        assertPasswordRejected(wrongPasswordFailure, "Internal login password does not match");
        verify(wrongPassword.accounts).findByUsername("alice");
        verify(wrongPassword.credentials).matches("raw password", "stored-password-hash");
        verifyNoInteractions(wrongPassword.eligibility);
    }

    @Test
    void eligibilityRejectionsForInactiveRoleAndGrantFailuresAreNotChanged() {
        for (String marker : Set.of("inactive", "role", "grant")) {
            Fixture fixture = fixture();
            AuthorizationAccount account = account();
            when(fixture.accounts.findByUsername("alice")).thenReturn(Optional.of(account));
            when(fixture.credentials.matches("raw password", "stored-password-hash")).thenReturn(true);
            when(fixture.eligibility.resolve(policy(), account))
                    .thenThrow(new InternalAccountAuthenticationRejectedException());

            assertRejected(() -> fixture.authenticator.authenticate(policy(), "alice", "raw password"));
            verify(fixture.eligibility).resolve(policy(), account);
        }
    }

    @Test
    void nonPasswordPoliciesAreStableConfigurationErrorsBeforeAnyLookupOrCredentialCall() {
        Fixture fixture = fixture();

        assertThatIllegalStateException().isThrownBy(() -> fixture.authenticator.authenticate(
                policy("email-code"), "alice", "raw password"))
                .withMessage("Internal password authentication policy is invalid");
        verifyNoInteractions(fixture.accounts, fixture.credentials, fixture.eligibility);
    }

    @Test
    void nullAndBlankInputsAreUniformRejectionsWithoutLookupOrCredentialLeakage() {
        Fixture fixture = fixture();

        assertPasswordRejected(catchThrowable(() -> fixture.authenticator.authenticate(policy(), null, "raw password")),
                "Internal login credentials are invalid");
        assertPasswordRejected(catchThrowable(() -> fixture.authenticator.authenticate(policy(), " ", "raw password")),
                "Internal login credentials are invalid");
        assertPasswordRejected(catchThrowable(() -> fixture.authenticator.authenticate(policy(), "alice", null)),
                "Internal login credentials are invalid");
        assertPasswordRejected(catchThrowable(() -> fixture.authenticator.authenticate(policy(), "alice", " ")),
                "Internal login credentials are invalid");
        verifyNoInteractions(fixture.accounts, fixture.credentials, fixture.eligibility);
    }

    @Test
    void repositoryAndCredentialSystemFailuresAreNotTurnedIntoCredentialRejections() {
        Fixture repositoryFailure = fixture();
        IllegalStateException repositoryException = new IllegalStateException("repository unavailable");
        when(repositoryFailure.accounts.findByUsername("alice")).thenThrow(repositoryException);
        assertThatThrownBy(() -> repositoryFailure.authenticator.authenticate(policy(), "alice", "raw password"))
                .isSameAs(repositoryException);

        Fixture credentialFailure = fixture();
        AuthorizationAccount account = account();
        IllegalStateException credentialException = new IllegalStateException("crypto unavailable");
        when(credentialFailure.accounts.findByUsername("alice")).thenReturn(Optional.of(account));
        when(credentialFailure.credentials.matches("raw password", "stored-password-hash"))
                .thenThrow(credentialException);
        assertThatThrownBy(() -> credentialFailure.authenticator.authenticate(policy(), "alice", "raw password"))
                .isSameAs(credentialException);
    }

    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        Throwable thrown = catchThrowable(callable);
        assertThat(thrown).isInstanceOf(InternalAccountAuthenticationRejectedException.class);
        assertThat(thrown.getMessage()).isEqualTo(InternalAccountAuthenticationRejectedException.MESSAGE)
                .doesNotContain("alice", "raw password");
    }

    private static void assertPasswordRejected(Throwable thrown, String logMessage) {
        assertThat(thrown).isInstanceOf(PasswordIncorrectException.class)
                .isInstanceOf(com.jacolp.common.core.exception.AuthenticationException.class);
        assertThat(thrown.getMessage()).isEqualTo(PasswordIncorrectException.MESSAGE)
                .doesNotContain("alice", "raw password");
        assertThat(((PasswordIncorrectException) thrown).getLogMessage()).isEqualTo(logMessage);
    }

    private static Fixture fixture() {
        AuthorizationAccountRepository accounts = mock(AuthorizationAccountRepository.class);
        PasswordCredentialVerifier credentials = mock(PasswordCredentialVerifier.class);
        InternalAccountEligibilityService eligibility = Mockito.mock(InternalAccountEligibilityService.class);
        return new Fixture(accounts, credentials, eligibility,
                new InternalPasswordAccountAuthenticator(accounts, credentials, eligibility));
    }

    private static InternalRegisteredClientPolicy policy() {
        return policy("password");
    }

    private static InternalRegisteredClientPolicy policy(String grantType) {
        return new InternalRegisteredClientPolicy("registered-user", "user", grantType, Set.of("*:read"),
                Set.of("*:read"), "127.0.0.1/32", Duration.ofHours(3), Duration.ofHours(72));
    }

    private static AuthorizationAccount account() {
        return new AuthorizationAccount(7L, "alice", "stored-password-hash", "alice@example.test", 3L, "", 1);
    }

    private static InternalAuthenticatedAccount authenticatedAccount() {
        return new InternalAuthenticatedAccount(7L, "alice", "alice@example.test", 3L, "USER", 3);
    }

    private record Fixture(AuthorizationAccountRepository accounts, PasswordCredentialVerifier credentials,
                           InternalAccountEligibilityService eligibility, InternalPasswordAccountAuthenticator authenticator) {
    }
}
