package com.jacolp.system.application.authorization;

import com.jacolp.common.security.oauth2.token.AccessTokenIssueRequest;
import com.jacolp.common.security.oauth2.token.IssuedAccessToken;
import com.jacolp.common.security.oauth2.token.IssuedRefreshToken;
import com.jacolp.common.security.oauth2.token.OAuth2RefreshTokenSessionService;
import com.jacolp.common.security.oauth2.token.RefreshTokenIssueRequest;
import com.jacolp.common.security.oauth2.token.Rs256AccessTokenIssuer;
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.InternalAuthenticatedAccount;
import com.jacolp.system.application.authorization.model.InternalIssuedTokens;
import com.jacolp.system.application.authorization.model.InternalLoginRequest;
import com.jacolp.system.application.authorization.model.InternalRefreshTokenRequest;
import com.jacolp.system.application.authorization.model.InternalRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.EmailLoginCodeAuthenticationRequest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalLoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

    @Test
    void passwordHappyPathCapturesRequestsAndPreservesOrder() {
        Fixture fixture = fixture("password");
        InternalLoginRequest request = passwordRequest(null, "192.0.2.7");
        when(fixture.password.authenticate(fixture.policy, "alice", "secret")).thenReturn(fixture.account);
        when(fixture.roles.resolve(3L)).thenReturn(fixture.effective);
        when(fixture.scopes.resolve(fixture.effective, fixture.policy.scopes(), fixture.policy.autoApproveScopes(), null))
                .thenReturn(fixture.grantedScopes);
        when(fixture.access.issue(any(AccessTokenIssueRequest.class))).thenReturn(fixture.accessToken);
        when(fixture.refresh.issue(any(RefreshTokenIssueRequest.class))).thenReturn(fixture.refreshToken);

        InternalIssuedTokens issued = fixture.service.login(request);

        assertThat(issued.accessToken()).isEqualTo("access-token");
        assertThat(issued.refreshToken()).isEqualTo("refresh-token");
        assertThat(issued.grantedScopes()).containsExactly("note:read");
        ArgumentCaptor<AccessTokenIssueRequest> accessRequest = ArgumentCaptor.forClass(AccessTokenIssueRequest.class);
        ArgumentCaptor<RefreshTokenIssueRequest> refreshRequest = ArgumentCaptor.forClass(RefreshTokenIssueRequest.class);
        verify(fixture.access).issue(accessRequest.capture());
        verify(fixture.refresh).issue(refreshRequest.capture());
        assertThat(accessRequest.getValue().userId()).isEqualTo(7L);
        assertThat(accessRequest.getValue().clientId()).isEqualTo("user");
        assertThat(accessRequest.getValue().grantType()).isEqualTo("password");
        assertThat(accessRequest.getValue().username()).isEqualTo("alice");
        assertThat(accessRequest.getValue().role()).isEqualTo("USER");
        assertThat(accessRequest.getValue().scopes()).containsExactly("note:read");
        assertThat(refreshRequest.getValue().userId()).isEqualTo(7L);
        assertThat(refreshRequest.getValue().clientId()).isEqualTo("user");
        assertThat(refreshRequest.getValue().grantedScopes()).containsExactly("note:read");
        assertThat(refreshRequest.getValue().accessToken().jti()).isEqualTo(fixture.accessToken.jti());
        assertThat(refreshRequest.getValue().accessToken().expiresAt()).isEqualTo(fixture.accessToken.expiresAt());

        InOrder order = inOrder(fixture.policyResolver, fixture.password, fixture.roles, fixture.scopes,
                fixture.access, fixture.refresh);
        order.verify(fixture.policyResolver).resolve("user", "password");
        order.verify(fixture.password).authenticate(fixture.policy, "alice", "secret");
        order.verify(fixture.roles).resolve(3L);
        order.verify(fixture.scopes).resolve(fixture.effective, fixture.policy.scopes(),
                fixture.policy.autoApproveScopes(), null);
        order.verify(fixture.access).issue(any(AccessTokenIssueRequest.class));
        order.verify(fixture.refresh).issue(any(RefreshTokenIssueRequest.class));
        verify(fixture.email, never()).authenticate(any(), any());
    }

    @Test
    void emailHappyPathUsesOnlyEmailAuthenticator() {
        Fixture fixture = fixture("email-code");
        InternalLoginRequest request = emailRequest(null, "192.0.2.7");
        when(fixture.email.authenticate(fixture.policy, new EmailLoginCodeAuthenticationRequest(
                "alice@example.test", "012345"))).thenReturn(fixture.account);
        when(fixture.roles.resolve(3L)).thenReturn(fixture.effective);
        when(fixture.scopes.resolve(fixture.effective, fixture.policy.scopes(), fixture.policy.autoApproveScopes(), null))
                .thenReturn(fixture.grantedScopes);
        when(fixture.access.issue(any(AccessTokenIssueRequest.class))).thenReturn(fixture.accessToken);
        when(fixture.refresh.issue(any(RefreshTokenIssueRequest.class))).thenReturn(fixture.refreshToken);

        fixture.service.login(request);

        verify(fixture.email).authenticate(eq(fixture.policy), any());
        verify(fixture.password, never()).authenticate(any(), any(), any());
    }

    @Test
    void refreshGrantDispatchesOnlyToTheDedicatedInternalRefreshService() {
        Fixture fixture = fixture("password");
        InternalIssuedTokens refreshed = new InternalIssuedTokens("new-access", "new-refresh", "Bearer", NOW,
                NOW.plus(Duration.ofMinutes(5)), NOW.plus(Duration.ofHours(24)), List.of("note:read"));
        when(fixture.internalRefresh.refresh(new InternalRefreshTokenRequest("user", "B".repeat(43),
                List.of("note:read"), "192.0.2.7"))).thenReturn(refreshed);

        Assertions.assertThat(fixture.service.login(new InternalLoginRequest("user", "refresh_token", null, null, null, null,
                Set.of("note:read"), "192.0.2.7", "B".repeat(43)))).isSameAs(refreshed);

        verify(fixture.internalRefresh).refresh(new InternalRefreshTokenRequest("user", "B".repeat(43),
                List.of("note:read"), "192.0.2.7"));
        verify(fixture.policyResolver, never()).resolve(any(), any());
        verify(fixture.password, never()).authenticate(any(), any(), any());
        verify(fixture.email, never()).authenticate(any(), any());
        verify(fixture.refresh, never()).issue(any());
    }

    @Test
    void requestedScopesNullAndEmptyArePassedUnchanged() {
        for (Set<String> requested : new Set[]{null, Set.of()}) {
            Set<String> requestedScopes = requested;
            Fixture fixture = fixture("password");
            InternalLoginRequest request = passwordRequest(requestedScopes, "192.0.2.7");
            when(fixture.password.authenticate(fixture.policy, "alice", "secret")).thenReturn(fixture.account);
            when(fixture.roles.resolve(3L)).thenReturn(fixture.effective);
            when(fixture.scopes.resolve(fixture.effective, fixture.policy.scopes(), fixture.policy.autoApproveScopes(), requestedScopes))
                    .thenReturn(fixture.grantedScopes);
            when(fixture.access.issue(any())).thenReturn(fixture.accessToken);
            when(fixture.refresh.issue(any())).thenReturn(fixture.refreshToken);

            fixture.service.login(request);

            verify(fixture.scopes).resolve(fixture.effective, fixture.policy.scopes(),
                    fixture.policy.autoApproveScopes(), requestedScopes);
        }
    }

    @Test
    void ipDenialAndInvalidRemoteAreUniformRejectionsBeforeAuthentication() {
        Fixture denied = fixture(policy("user", "password", "198.51.100.0/24"));
        when(denied.policyResolver.resolve("user", "password")).thenReturn(denied.policy);
        assertThatThrownBy(() -> denied.service.login(passwordRequest(null, "192.0.2.7")))
                .isInstanceOf(InternalAccountAuthenticationRejectedException.class)
                .hasMessage("当前 IP 不在允许的登录范围内")
                .satisfies(thrown -> assertThat(((InternalAccountAuthenticationRejectedException) thrown).reason())
                        .isEqualTo(InternalAccountAuthenticationRejectedException.Reason.IP_NOT_ALLOWED));
        verify(denied.password, never()).authenticate(any(), any(), any());

        Fixture invalidRemote = fixture("password");
        assertThatThrownBy(() -> invalidRemote.service.login(passwordRequest(null, "not-an-ip")))
                .isInstanceOf(InternalAccountAuthenticationRejectedException.class)
                .hasMessage("当前 IP 不在允许的登录范围内");
        verify(invalidRemote.password, never()).authenticate(any(), any(), any());
    }

    @Test
    void ipv6LoopbackIsAllowedByTheDualStackInternalClientPolicy() {
        Fixture allowed = fixture(policy("user", "password", "0.0.0.0/0,::/0"));
        when(allowed.password.authenticate(allowed.policy, "alice", "secret")).thenReturn(allowed.account);
        when(allowed.roles.resolve(3L)).thenReturn(allowed.effective);
        when(allowed.scopes.resolve(allowed.effective, allowed.policy.scopes(), allowed.policy.autoApproveScopes(), null))
                .thenReturn(allowed.grantedScopes);
        when(allowed.access.issue(any(AccessTokenIssueRequest.class))).thenReturn(allowed.accessToken);
        when(allowed.refresh.issue(any(RefreshTokenIssueRequest.class))).thenReturn(allowed.refreshToken);

        allowed.service.login(passwordRequest(null, "0:0:0:0:0:0:0:1"));

        verify(allowed.password).authenticate(allowed.policy, "alice", "secret");
    }

    @Test
    void invalidIpConfigurationFailsAsIllegalStateBeforeAuthentication() {
        Fixture fixture = fixture(policy("user", "password", "192.0.2.0/33"));
        when(fixture.policyResolver.resolve("user", "password")).thenReturn(fixture.policy);

        assertThatThrownBy(() -> fixture.service.login(passwordRequest(null, "192.0.2.7")))
                .isInstanceOf(IllegalStateException.class);
        verify(fixture.password, never()).authenticate(any(), any(), any());
    }

    @Test
    void nullAndExceptionDependenciesPropagateAndNullResultsFailClosed() {
        Fixture policyNull = fixture("password");
        when(policyNull.policyResolver.resolve("user", "password")).thenReturn(null);
        assertThatThrownBy(() -> policyNull.service.login(passwordRequest(null, "192.0.2.7")))
                .isInstanceOf(IllegalStateException.class);

        Fixture authNull = fixture("password");
        when(authNull.password.authenticate(authNull.policy, "alice", "secret")).thenReturn(null);
        assertThatThrownBy(() -> authNull.service.login(passwordRequest(null, "192.0.2.7")))
                .isInstanceOf(IllegalStateException.class);

        Fixture roleNull = preparedFixture();
        when(roleNull.roles.resolve(3L)).thenReturn(null);
        assertThatThrownBy(() -> roleNull.service.login(passwordRequest(null, "192.0.2.7")))
                .isInstanceOf(IllegalStateException.class);

        Fixture scopesNull = preparedFixture();
        when(scopesNull.scopes.resolve(any(), any(), any(), isNull())).thenReturn(null);
        assertThatThrownBy(() -> scopesNull.service.login(passwordRequest(null, "192.0.2.7")))
                .isInstanceOf(IllegalStateException.class);

        Fixture scopesEmpty = preparedFixture();
        when(scopesEmpty.scopes.resolve(any(), any(), any(), isNull())).thenReturn(List.of());
        assertThatThrownBy(() -> scopesEmpty.service.login(passwordRequest(null, "192.0.2.7")))
                .isInstanceOf(InternalAccountAuthenticationRejectedException.class)
                .hasMessage("当前账号没有可用的访问权限");

        Fixture accessNull = preparedFixture();
        when(accessNull.access.issue(any())).thenReturn(null);
        assertThatThrownBy(() -> accessNull.service.login(passwordRequest(null, "192.0.2.7")))
                .isInstanceOf(IllegalStateException.class);

        Fixture refreshNull = preparedFixture();
        when(refreshNull.refresh.issue(any())).thenReturn(null);
        assertThatThrownBy(() -> refreshNull.service.login(passwordRequest(null, "192.0.2.7")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void dependencyExceptionsAreNotSwallowed() {
        Fixture fixture = fixture("password");
        RuntimeException failure = new RuntimeException("policy");
        when(fixture.policyResolver.resolve("user", "password")).thenThrow(failure);
        assertThatThrownBy(() -> fixture.service.login(passwordRequest(null, "192.0.2.7"))).isSameAs(failure);

        Fixture auth = fixture("password");
        failure = new RuntimeException("auth");
        when(auth.password.authenticate(auth.policy, "alice", "secret")).thenThrow(failure);
        assertThatThrownBy(() -> auth.service.login(passwordRequest(null, "192.0.2.7"))).isSameAs(failure);

        Fixture role = preparedFixture();
        failure = new RuntimeException("role");
        when(role.roles.resolve(3L)).thenThrow(failure);
        assertThatThrownBy(() -> role.service.login(passwordRequest(null, "192.0.2.7"))).isSameAs(failure);

        Fixture scope = preparedFixture();
        failure = new RuntimeException("scope");
        when(scope.scopes.resolve(any(), any(), any(), isNull())).thenThrow(failure);
        assertThatThrownBy(() -> scope.service.login(passwordRequest(null, "192.0.2.7"))).isSameAs(failure);

        Fixture access = preparedFixture();
        failure = new RuntimeException("access");
        when(access.access.issue(any())).thenThrow(failure);
        assertThatThrownBy(() -> access.service.login(passwordRequest(null, "192.0.2.7"))).isSameAs(failure);

        Fixture refresh = preparedFixture();
        failure = new RuntimeException("refresh");
        when(refresh.refresh.issue(any())).thenThrow(failure);
        assertThatThrownBy(() -> refresh.service.login(passwordRequest(null, "192.0.2.7"))).isSameAs(failure);
    }

    @Test
    void wrongAuthenticatedRoleOrEffectiveRoleFailsClosed() {
        Fixture accountNull = preparedFixture();
        when(accountNull.password.authenticate(accountNull.policy, "alice", "secret")).thenReturn(null);
        assertThatThrownBy(() -> accountNull.service.login(passwordRequest(null, "192.0.2.7")))
                .isInstanceOf(IllegalStateException.class);

        Fixture wrongRole = preparedFixture();
        InternalAuthenticatedAccount role = new InternalAuthenticatedAccount(7L, "alice", "alice@example.test",
                4L, "USER", 3);
        when(wrongRole.password.authenticate(wrongRole.policy, "alice", "secret")).thenReturn(role);
        assertThatThrownBy(() -> wrongRole.service.login(passwordRequest(null, "192.0.2.7")))
                .isInstanceOf(IllegalStateException.class);

        Fixture wrongEffective = preparedFixture();
        when(wrongEffective.roles.resolve(3L)).thenReturn(new EffectiveRolePermissions(3L, "ADMIN", 3,
                List.of("note:read")));
        assertThatThrownBy(() -> wrongEffective.service.login(passwordRequest(null, "192.0.2.7")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Fixture preparedFixture() {
        Fixture fixture = fixture("password");
        when(fixture.password.authenticate(fixture.policy, "alice", "secret")).thenReturn(fixture.account);
        when(fixture.roles.resolve(3L)).thenReturn(fixture.effective);
        when(fixture.scopes.resolve(fixture.effective, fixture.policy.scopes(), fixture.policy.autoApproveScopes(), null))
                .thenReturn(fixture.grantedScopes);
        when(fixture.access.issue(any())).thenReturn(fixture.accessToken);
        when(fixture.refresh.issue(any())).thenReturn(fixture.refreshToken);
        return fixture;
    }

    private static Fixture fixture(String grantType) {
        return fixture(policy("user", grantType, "192.0.2.0/24"));
    }

    private static Fixture fixture(InternalRegisteredClientPolicy policy) {
        InternalRegisteredClientPolicyResolver policyResolver = Mockito.mock(InternalRegisteredClientPolicyResolver.class);
        InternalPasswordAccountAuthenticator password = Mockito.mock(InternalPasswordAccountAuthenticator.class);
        EmailLoginCodeAuthenticator email = Mockito.mock(EmailLoginCodeAuthenticator.class);
        EffectiveRolePermissionResolver roles = Mockito.mock(EffectiveRolePermissionResolver.class);
        OAuth2ScopeResolver scopes = Mockito.mock(OAuth2ScopeResolver.class);
        Rs256AccessTokenIssuer accessIssuer = mock(Rs256AccessTokenIssuer.class);
        OAuth2RefreshTokenSessionService refreshService = mock(OAuth2RefreshTokenSessionService.class);
        InternalRefreshTokenService internalRefreshService = Mockito.mock(InternalRefreshTokenService.class);
        when(policyResolver.resolve(policy.clientId(), policy.grantType())).thenReturn(policy);
        InternalAuthenticatedAccount account = new InternalAuthenticatedAccount(7L, "alice", "alice@example.test",
                3L, "USER", 3);
        EffectiveRolePermissions effective = new EffectiveRolePermissions(3L, "USER", 3, List.of("note:read"));
        IssuedAccessToken issuedAccess = new IssuedAccessToken("access-token", "A".repeat(22), NOW,
                NOW.plus(Duration.ofMinutes(5)));
        IssuedRefreshToken issuedRefresh = new IssuedRefreshToken("refresh-token", NOW, NOW.plus(Duration.ofHours(24)));
        return new Fixture(new InternalLoginService(policyResolver, password, email, roles, scopes,
                accessIssuer, refreshService, internalRefreshService), policyResolver, password, email, roles, scopes, accessIssuer,
                refreshService, internalRefreshService, policy, account, effective, List.of("note:read"), issuedAccess,
                issuedRefresh);
    }

    private static InternalRegisteredClientPolicy policy(String clientId, String grantType, String allowedIps) {
        return new InternalRegisteredClientPolicy("internal", clientId, grantType, Set.of("note:read"),
                Set.of("note:read"), allowedIps, Duration.ofMinutes(5), Duration.ofHours(24));
    }

    private static InternalLoginRequest passwordRequest(Set<String> scopes, String remoteAddress) {
        return new InternalLoginRequest("user", "password", "alice", "secret", null, null, scopes, remoteAddress);
    }

    private static InternalLoginRequest emailRequest(Set<String> scopes, String remoteAddress) {
        return new InternalLoginRequest("user", "email-code", null, null, "alice@example.test", "012345", scopes,
                remoteAddress);
    }

    private record Fixture(InternalLoginService service, InternalRegisteredClientPolicyResolver policyResolver,
                           InternalPasswordAccountAuthenticator password, EmailLoginCodeAuthenticator email,
                           EffectiveRolePermissionResolver roles, OAuth2ScopeResolver scopes,
                           Rs256AccessTokenIssuer access, OAuth2RefreshTokenSessionService refresh,
                           InternalRefreshTokenService internalRefresh,
                           InternalRegisteredClientPolicy policy, InternalAuthenticatedAccount account,
                           EffectiveRolePermissions effective, List<String> grantedScopes,
                           IssuedAccessToken accessToken, IssuedRefreshToken refreshToken) {
    }
}
