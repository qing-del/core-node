package com.jacolp.system.application.authorization;

import com.jacolp.constant.UserConstant;
import com.jacolp.common.core.exception.AuthenticationException;
import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.authorization.model.CoreAgentBrowserPrincipal;
import com.jacolp.system.application.authorization.model.RoleMetadata;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.system.application.port.out.PasswordCredentialVerifier;
import com.jacolp.system.application.port.out.RoleMetadataRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CoreAgentBrowserAccountAuthenticatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"CREATOR", "ADMIN", "USER"})
    void authenticatesEverySupportedRoleOnlyAfterAccountPasswordAndRoleChecks(String roleCode) {
        Fixture fixture = fixture();
        AuthorizationAccount account = account(UserConstant.ACTIVE_STATUS, "");
        when(fixture.accounts.findByUsername("alice")).thenReturn(Optional.of(account));
        when(fixture.credentials.matches("raw-password", "password-hash")).thenReturn(true);
        when(fixture.roles.findById(2L)).thenReturn(Optional.of(role(2L, roleCode, 3)));

        CoreAgentBrowserPrincipal principal = fixture.authenticator.authenticate("alice", "raw-password");

        assertThat(principal.userId()).isEqualTo(7L);
        assertThat(principal.username()).isEqualTo("alice");
        assertThat(principal.roleId()).isEqualTo(2L);
        assertThat(principal.roleCode()).isEqualTo(roleCode);
        assertThat(principal.rank()).isEqualTo(3);
        InOrder order = inOrder(fixture.accounts, fixture.credentials, fixture.roles);
        order.verify(fixture.accounts).findByUsername("alice");
        order.verify(fixture.credentials).matches("raw-password", "password-hash");
        order.verify(fixture.roles).findById(2L);
    }

    @Test
    void missingAccountUsesOneDummyCredentialCheckAndWrongPasswordUsesOneStoredHashCheck() {
        Fixture missing = fixture();
        when(missing.accounts.findByUsername("alice")).thenReturn(Optional.empty());
        when(missing.credentials.matches("raw-password", null)).thenReturn(false);
        assertRejected(() -> missing.authenticator.authenticate("alice", "raw-password"));
        verify(missing.credentials).matches("raw-password", null);
        Mockito.verifyNoInteractions(missing.roles);

        Fixture wrong = fixture();
        when(wrong.accounts.findByUsername("alice")).thenReturn(Optional.of(account(UserConstant.ACTIVE_STATUS, "")));
        when(wrong.credentials.matches("raw-password", "password-hash")).thenReturn(false);
        assertRejected(() -> wrong.authenticator.authenticate("alice", "raw-password"));
        verify(wrong.credentials).matches("raw-password", "password-hash");
        Mockito.verifyNoInteractions(wrong.roles);
    }

    @Test
    void blankCredentialsRejectWithoutRepositoryOrCredentialCalls() {
        Fixture fixture = fixture();
        assertRejected(() -> fixture.authenticator.authenticate(null, "raw-password"));
        assertRejected(() -> fixture.authenticator.authenticate(" ", "raw-password"));
        assertRejected(() -> fixture.authenticator.authenticate("alice", null));
        assertRejected(() -> fixture.authenticator.authenticate("alice", " "));
        verifyNoInteractions(fixture.accounts, fixture.credentials, fixture.roles);
    }

    @Test
    void inactiveAndAuthorizationCodeDeniedAccountsUseTheSameRejection() {
        Fixture inactive = fixture();
        when(inactive.accounts.findByUsername("alice")).thenReturn(Optional.of(account(9, "")));
        when(inactive.credentials.matches("raw-password", "password-hash")).thenReturn(true);
        assertRejected(() -> inactive.authenticator.authenticate("alice", "raw-password"));
        Mockito.verifyNoInteractions(inactive.roles);

        AccountGrantTypeResolver deniedGrantResolver = mock(AccountGrantTypeResolver.class);
        when(deniedGrantResolver.allows(eq(AccountGrantTypeResolver.AUTHORIZATION_CODE), any())).thenReturn(false);
        Fixture denied = fixture(deniedGrantResolver);
        when(denied.accounts.findByUsername("alice")).thenReturn(Optional.of(account(UserConstant.ACTIVE_STATUS, "")));
        when(denied.credentials.matches("raw-password", "password-hash")).thenReturn(true);
        assertRejected(() -> denied.authenticator.authenticate("alice", "raw-password"));
        Mockito.verifyNoInteractions(denied.roles);
    }

    @Test
    void extraGrantConfigurationPollutionIsAStableConfigurationError() {
        Fixture fixture = fixture();
        when(fixture.accounts.findByUsername("alice")).thenReturn(Optional.of(
                account(UserConstant.ACTIVE_STATUS, "authorization_code")));
        when(fixture.credentials.matches("raw-password", "password-hash")).thenReturn(true);

        assertThatIllegalStateException().isThrownBy(() -> fixture.authenticator.authenticate("alice", "raw-password"))
                .withMessageContaining("grant configuration");
        Mockito.verifyNoInteractions(fixture.roles);
    }

    @Test
    void missingOrPollutedRoleMetadataIsAConfigurationErrorAfterPasswordVerification() {
        Fixture missing = readyFixture();
        when(missing.roles.findById(2L)).thenReturn(Optional.empty());
        assertThatIllegalStateException().isThrownBy(() -> missing.authenticator.authenticate("alice", "raw-password"))
                .withMessageContaining("role metadata");

        Fixture polluted = readyFixture();
        when(polluted.roles.findById(2L)).thenReturn(Optional.of(role(99L, "VIP", 0)));
        assertThatIllegalStateException().isThrownBy(() -> polluted.authenticator.authenticate("alice", "raw-password"))
                .withMessageContaining("role metadata");
    }

    @Test
    void repositoryCredentialAndRoleMetadataSystemFailuresPropagate() {
        Fixture accountFailure = fixture();
        IllegalStateException unavailable = new IllegalStateException("account database unavailable");
        when(accountFailure.accounts.findByUsername("alice")).thenThrow(unavailable);
        assertThatThrownBy(() -> accountFailure.authenticator.authenticate("alice", "raw-password")).isSameAs(unavailable);

        Fixture cryptoFailure = fixture();
        when(cryptoFailure.accounts.findByUsername("alice")).thenReturn(Optional.of(account(UserConstant.ACTIVE_STATUS, "")));
        IllegalStateException cryptoUnavailable = new IllegalStateException("crypto unavailable");
        when(cryptoFailure.credentials.matches("raw-password", "password-hash")).thenThrow(cryptoUnavailable);
        assertThatThrownBy(() -> cryptoFailure.authenticator.authenticate("alice", "raw-password"))
                .isSameAs(cryptoUnavailable);

        Fixture roleFailure = readyFixture();
        IllegalStateException roleUnavailable = new IllegalStateException("role database unavailable");
        when(roleFailure.roles.findById(2L)).thenThrow(roleUnavailable);
        assertThatThrownBy(() -> roleFailure.authenticator.authenticate("alice", "raw-password")).isSameAs(roleUnavailable);
    }

    @Test
    void principalAndRejectionDiagnosticsDoNotExposeCredentialOrAccountSecrets() {
        CoreAgentBrowserPrincipal principal = new CoreAgentBrowserPrincipal(7L, "alice", 2L, "USER", 3);
        assertThat(principal.toString()).doesNotContain("alice", "password-hash", "alice@example.test");
        Assertions.assertThat(new CoreAgentBrowserAuthenticationRejectedException().getMessage())
                .isEqualTo(CoreAgentBrowserAuthenticationRejectedException.MESSAGE)
                .doesNotContain("alice", "raw-password");
    }

    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOf(AuthenticationException.class)
                .hasMessage(CoreAgentBrowserAuthenticationRejectedException.MESSAGE);
    }

    private static Fixture readyFixture() {
        Fixture fixture = fixture();
        when(fixture.accounts.findByUsername("alice")).thenReturn(Optional.of(account(UserConstant.ACTIVE_STATUS, "")));
        when(fixture.credentials.matches("raw-password", "password-hash")).thenReturn(true);
        return fixture;
    }

    private static Fixture fixture() {
        return fixture(new AccountGrantTypeResolver(AccountGrantTypeResolver.requiredDefaultGrantTypes()));
    }

    private static Fixture fixture(AccountGrantTypeResolver grantTypeResolver) {
        AuthorizationAccountRepository accounts = mock(AuthorizationAccountRepository.class);
        PasswordCredentialVerifier credentials = mock(PasswordCredentialVerifier.class);
        RoleMetadataRepository roles = mock(RoleMetadataRepository.class);
        return new Fixture(accounts, credentials, roles,
                new CoreAgentBrowserAccountAuthenticator(accounts, credentials, grantTypeResolver, roles));
    }

    private static AuthorizationAccount account(int status, String extraGrantTypes) {
        return new AuthorizationAccount(7L, "alice", "password-hash", "alice@example.test", 2L, extraGrantTypes, status);
    }

    private static RoleMetadata role(Long id, String code, int rank) {
        return new RoleMetadata(id, code + " role", code, rank, 100, 1_000L, null, null);
    }

    private record Fixture(AuthorizationAccountRepository accounts,
                           PasswordCredentialVerifier credentials,
                           RoleMetadataRepository roles,
                           CoreAgentBrowserAccountAuthenticator authenticator) {
    }
}
