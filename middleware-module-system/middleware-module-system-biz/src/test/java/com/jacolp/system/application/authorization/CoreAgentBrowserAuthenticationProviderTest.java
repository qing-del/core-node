package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.CoreAgentBrowserPrincipal;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CoreAgentBrowserAuthenticationProviderTest {

    @ParameterizedTest
    @ValueSource(strings = {"CREATOR", "ADMIN", "USER"})
    void returnsAReadOnlyAuthenticatedTokenWithStableUserIdNameAndOneRoleAuthority(String roleCode) {
        CoreAgentBrowserAccountAuthenticator authenticator = Mockito.mock(CoreAgentBrowserAccountAuthenticator.class);
        CoreAgentBrowserPrincipal principal = new CoreAgentBrowserPrincipal(7L, "alice", 2L, roleCode, 3);
        when(authenticator.authenticate("alice", "raw-password")).thenReturn(principal);
        CoreAgentBrowserAuthenticationProvider provider = new CoreAgentBrowserAuthenticationProvider(authenticator);

        Authentication authentication = provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("alice", "raw-password"));

        assertThat(authentication).isInstanceOf(CoreAgentBrowserAuthenticationToken.class);
        CoreAgentBrowserAuthenticationToken token = (CoreAgentBrowserAuthenticationToken) authentication;
        Assertions.assertThat(token.getPrincipal()).isSameAs(principal);
        assertThat(token.getCredentials()).isNull();
        assertThat(token.getName()).isEqualTo("7");
        assertThat(token.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_" + roleCode);
        assertThat(token.getDetails()).isNull();
    }

    @Test
    void ordinaryApplicationRejectionBecomesOnePiiFreeBadCredentialsFailure() {
        CoreAgentBrowserAccountAuthenticator authenticator = Mockito.mock(CoreAgentBrowserAccountAuthenticator.class);
        when(authenticator.authenticate("alice", "raw-password"))
                .thenThrow(new CoreAgentBrowserAuthenticationRejectedException());
        CoreAgentBrowserAuthenticationProvider provider = new CoreAgentBrowserAuthenticationProvider(authenticator);

        assertThatThrownBy(() -> provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("alice",
                "raw-password"))).isInstanceOf(BadCredentialsException.class)
                .hasMessage(CoreAgentBrowserAuthenticationProvider.BAD_CREDENTIALS_MESSAGE);
    }

    @Test
    void unsupportedNullAndNonStringInputsFailClosedWithoutCallingApplicationAuthentication() {
        CoreAgentBrowserAccountAuthenticator authenticator = Mockito.mock(CoreAgentBrowserAccountAuthenticator.class);
        CoreAgentBrowserAuthenticationProvider provider = new CoreAgentBrowserAuthenticationProvider(authenticator);

        assertThatThrownBy(() -> provider.authenticate(null)).isInstanceOf(AuthenticationServiceException.class);
        assertThatThrownBy(() -> provider.authenticate(new TestingAuthenticationToken("alice", "raw-password")))
                .isInstanceOf(AuthenticationServiceException.class);
        assertThatThrownBy(() -> provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(7L, 99L)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(CoreAgentBrowserAuthenticationProvider.BAD_CREDENTIALS_MESSAGE);
        verifyNoInteractions(authenticator);
    }

    @Test
    void applicationSystemFailuresAreNotMisrepresentedAsBadCredentials() {
        CoreAgentBrowserAccountAuthenticator authenticator = Mockito.mock(CoreAgentBrowserAccountAuthenticator.class);
        IllegalStateException unavailable = new IllegalStateException("repository unavailable");
        when(authenticator.authenticate("alice", "raw-password")).thenThrow(unavailable);
        CoreAgentBrowserAuthenticationProvider provider = new CoreAgentBrowserAuthenticationProvider(authenticator);

        assertThatThrownBy(() -> provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("alice",
                "raw-password"))).isSameAs(unavailable);
    }

    @Test
    void tokenCannotBeElevatedAndItsDiagnosticsNeverExposeUserOrCredentials() {
        CoreAgentBrowserAuthenticationToken token = CoreAgentBrowserAuthenticationToken.authenticated(
                new CoreAgentBrowserPrincipal(7L, "alice", 2L, "USER", 3));

        assertThatIllegalArgumentException().isThrownBy(() -> token.setAuthenticated(true));
        token.eraseCredentials();
        assertThat(token.getCredentials()).isNull();
        assertThat(token.toString()).doesNotContain("alice", "raw-password", "password-hash", "alice@example.test");
    }

    @Test
    void supportsOnlyUsernamePasswordAuthenticationTokens() {
        CoreAgentBrowserAuthenticationProvider provider = new CoreAgentBrowserAuthenticationProvider(
                Mockito.mock(CoreAgentBrowserAccountAuthenticator.class));

        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
        assertThat(provider.supports(TestingAuthenticationToken.class)).isFalse();
        assertThat(provider.supports(null)).isFalse();
    }
}
