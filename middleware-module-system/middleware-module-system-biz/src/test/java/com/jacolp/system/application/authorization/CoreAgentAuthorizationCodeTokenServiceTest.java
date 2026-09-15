package com.jacolp.system.application.authorization;

import com.jacolp.constant.UserConstant;
import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.common.security.oauth2.token.AccessTokenIssueRequest;
import com.jacolp.common.security.oauth2.token.IssuedAccessToken;
import com.jacolp.common.security.oauth2.token.IssuedRefreshToken;
import com.jacolp.common.security.oauth2.token.OAuth2RefreshTokenSessionService;
import com.jacolp.common.security.oauth2.token.RefreshTokenIssueRequest;
import com.jacolp.common.security.oauth2.token.Rs256AccessTokenIssuer;
import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationCodeTokens;
import com.jacolp.system.application.authorization.model.VerifiedCoreAgentAuthorizationCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreAgentAuthorizationCodeTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

    @Test
    void signsCurrentIntersectionThenCreatesRefreshSessionWithPolicyTtls() {
        Fixture fixture = preparedFixture();

        IssuedCoreAgentAuthorizationCodeTokens tokens = fixture.service.issue(verified(false));

        assertThat(tokens.accessToken()).isEqualTo("access-token");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-token");
        assertThat(tokens.tokenType()).isEqualTo("Bearer");
        assertThat(tokens.accessIssuedAt()).isEqualTo(NOW);
        assertThat(tokens.accessExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(tokens.refreshIssuedAt()).isEqualTo(NOW);
        assertThat(tokens.refreshExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(tokens.grantedScopes()).containsExactly("note:read");
        assertThat(tokens.socketAddressChanged()).isFalse();

        ArgumentCaptor<AccessTokenIssueRequest> accessRequest = ArgumentCaptor.forClass(AccessTokenIssueRequest.class);
        ArgumentCaptor<RefreshTokenIssueRequest> refreshRequest = ArgumentCaptor.forClass(RefreshTokenIssueRequest.class);
        verify(fixture.accessIssuer).issue(accessRequest.capture());
        verify(fixture.refreshService).issue(refreshRequest.capture());
        assertThat(accessRequest.getValue().userId()).isEqualTo(7L);
        assertThat(accessRequest.getValue().clientId()).isEqualTo("core_agent");
        assertThat(accessRequest.getValue().grantType()).isEqualTo("authorization_code");
        assertThat(accessRequest.getValue().username()).isEqualTo("alice");
        assertThat(accessRequest.getValue().role()).isEqualTo("CREATOR");
        assertThat(accessRequest.getValue().scopes()).containsExactly("note:read");
        assertThat(accessRequest.getValue().tokenTtl()).isEqualTo(Duration.ofHours(1));
        assertThat(refreshRequest.getValue().userId()).isEqualTo(7L);
        assertThat(refreshRequest.getValue().clientId()).isEqualTo("core_agent");
        assertThat(refreshRequest.getValue().grantedScopes()).containsExactly("note:read");
        assertThat(refreshRequest.getValue().accessToken().jti()).isEqualTo("A".repeat(22));
        assertThat(refreshRequest.getValue().accessToken().expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(refreshRequest.getValue().refreshTtl()).isEqualTo(Duration.ofHours(24));

        InOrder order = inOrder(fixture.policyResolver, fixture.accounts, fixture.roles, fixture.scopes,
                fixture.accessIssuer, fixture.refreshService);
        order.verify(fixture.policyResolver).resolve("core_agent");
        order.verify(fixture.accounts).findById(7L);
        order.verify(fixture.roles).resolve(3L);
        order.verify(fixture.scopes).resolve(fixture.effectiveRole, fixture.policy.scopes(), Set.of(),
                List.of("note:read", "*:super"));
        order.verify(fixture.accessIssuer).issue(any());
        order.verify(fixture.refreshService).issue(any());
    }

    @Test
    void permissionNarrowingIsAppliedAndEmptyCurrentIntersectionIsRejected() {
        Fixture narrowed = preparedFixture();
        when(narrowed.scopes.resolve(narrowed.effectiveRole, narrowed.policy.scopes(), Set.of(),
                List.of("note:read", "*:super"))).thenReturn(List.of("note:read"));
        narrowed.service.issue(verified(true));
        verify(narrowed.accessIssuer).issue(any());

        Fixture empty = preparedFixture();
        when(empty.scopes.resolve(empty.effectiveRole, empty.policy.scopes(), Set.of(),
                List.of("note:read", "*:super"))).thenReturn(List.of());
        assertRejected(() -> empty.service.issue(verified(false)));
        verify(empty.accessIssuer, never()).issue(any());
        verify(empty.refreshService, never()).issue(any());
    }

    @Test
    void accountRoleAndClientIdentityChangesRejectBeforeTokenIssuance() {
        Fixture usernameChanged = preparedFixture();
        when(usernameChanged.accounts.findById(7L)).thenReturn(Optional.of(account("bob", 3L, UserConstant.ACTIVE_STATUS, "")));
        assertRejected(() -> usernameChanged.service.issue(verified(false)));
        verify(usernameChanged.roles, never()).resolve(any());

        Fixture roleChanged = preparedFixture();
        when(roleChanged.accounts.findById(7L)).thenReturn(Optional.of(account("alice", 4L, UserConstant.ACTIVE_STATUS, "")));
        assertRejected(() -> roleChanged.service.issue(verified(false)));

        Fixture clientChanged = preparedFixture();
        CoreAgentRegisteredClientPolicy changedPolicy = new CoreAgentRegisteredClientPolicy("changed-id", "core_agent",
                "http://127.0.0.1:9090/oauth/callback", Set.of("note:read"), Set.of("note:read"), "0.0.0.0/0",
                Duration.ofHours(1), Duration.ofHours(24), Duration.ofMinutes(10));
        when(clientChanged.policyResolver.resolve("core_agent")).thenReturn(changedPolicy);
        assertRejected(() -> clientChanged.service.issue(verified(false)));
        verify(clientChanged.accounts, never()).findById(7L);

        Fixture malformedRole = preparedFixture();
        when(malformedRole.roles.resolve(3L)).thenReturn(new EffectiveRolePermissions(3L, "", 1, List.of("note:read")));
        assertThatIllegalStateException().isThrownBy(() -> malformedRole.service.issue(verified(false)));
    }

    @Test
    void inactiveOrGrantConfigurationChangesAreRejectedOrFailClosed() {
        Fixture inactive = preparedFixture();
        when(inactive.accounts.findById(7L)).thenReturn(Optional.of(account("alice", 3L, UserConstant.ACTIVE_STATUS + 1, "")));
        assertRejected(() -> inactive.service.issue(verified(false)));

        Fixture polluted = preparedFixture();
        when(polluted.accounts.findById(7L)).thenReturn(Optional.of(account("alice", 3L, UserConstant.ACTIVE_STATUS,
                "authorization_code")));
        assertThatIllegalStateException().isThrownBy(() -> polluted.service.issue(verified(false)));
    }

    @Test
    void issuerAndRefreshFailuresNeverReturnPartialTokenResults() {
        Fixture accessFailure = preparedFixture();
        IllegalStateException accessException = new IllegalStateException("access unavailable");
        when(accessFailure.accessIssuer.issue(any())).thenThrow(accessException);
        assertThatThrownBy(() -> accessFailure.service.issue(verified(false))).isSameAs(accessException);
        verify(accessFailure.refreshService, never()).issue(any());

        Fixture refreshFailure = preparedFixture();
        IllegalStateException refreshException = new IllegalStateException("refresh unavailable");
        when(refreshFailure.refreshService.issue(any())).thenThrow(refreshException);
        assertThatThrownBy(() -> refreshFailure.service.issue(verified(false))).isSameAs(refreshException);
    }

    @Test
    void nullDependencyResultsFailClosedAndDiagnosticsRedactTokens() {
        Fixture nullAccess = preparedFixture();
        when(nullAccess.accessIssuer.issue(any())).thenReturn(null);
        assertThatIllegalStateException().isThrownBy(() -> nullAccess.service.issue(verified(false)));

        Fixture nullRefresh = preparedFixture();
        when(nullRefresh.refreshService.issue(any())).thenReturn(null);
        assertThatIllegalStateException().isThrownBy(() -> nullRefresh.service.issue(verified(false)));

        IssuedCoreAgentAuthorizationCodeTokens tokens = new IssuedCoreAgentAuthorizationCodeTokens("access-secret",
                "refresh-secret", "Bearer", NOW, NOW.plusSeconds(1), NOW, NOW.plusSeconds(2), List.of("note:read"), true);
        assertThat(tokens.toString()).contains("<redacted>").doesNotContain("access-secret", "refresh-secret");
        assertThatIllegalArgumentException().isThrownBy(() -> new IssuedCoreAgentAuthorizationCodeTokens("a", "b", "Bearer",
                NOW, NOW.plusSeconds(1), NOW, NOW.plusSeconds(2), List.of(), false));
    }

    private static void assertRejected(org.junit.jupiter.api.function.Executable executable) {
        assertThatThrownBy(() -> executable.execute()).isInstanceOf(CoreAgentAuthorizationCodeTokenRejectedException.class)
                .hasMessage(CoreAgentAuthorizationCodeTokenRejectedException.MESSAGE);
    }

    private static Fixture preparedFixture() {
        CoreAgentRegisteredClientPolicyResolver policyResolver = Mockito.mock(CoreAgentRegisteredClientPolicyResolver.class);
        AuthorizationAccountRepository accounts = mock(AuthorizationAccountRepository.class);
        EffectiveRolePermissionResolver roles = Mockito.mock(EffectiveRolePermissionResolver.class);
        OAuth2ScopeResolver scopes = Mockito.mock(OAuth2ScopeResolver.class);
        Rs256AccessTokenIssuer accessIssuer = mock(Rs256AccessTokenIssuer.class);
        OAuth2RefreshTokenSessionService refreshService = mock(OAuth2RefreshTokenSessionService.class);
        CoreAgentRegisteredClientPolicy policy = new CoreAgentRegisteredClientPolicy("registered-core-agent", "core_agent",
                "http://127.0.0.1:9090/oauth/callback", Set.of("note:read", "*:super"), Set.of("note:read"),
                "0.0.0.0/0", Duration.ofHours(1), Duration.ofHours(24), Duration.ofMinutes(10));
        AuthorizationAccount account = account("alice", 3L, UserConstant.ACTIVE_STATUS, "");
        EffectiveRolePermissions effectiveRole = new EffectiveRolePermissions(3L, "CREATOR", 1,
                List.of("note:read", "*:super"));
        IssuedAccessToken accessToken = new IssuedAccessToken("access-token", "A".repeat(22), NOW,
                NOW.plus(Duration.ofHours(1)));
        IssuedRefreshToken refreshToken = new IssuedRefreshToken("refresh-token", NOW, NOW.plus(Duration.ofHours(24)));
        when(policyResolver.resolve("core_agent")).thenReturn(policy);
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(roles.resolve(3L)).thenReturn(effectiveRole);
        when(scopes.resolve(effectiveRole, policy.scopes(), Set.of(), List.of("note:read", "*:super")))
                .thenReturn(List.of("note:read"));
        when(accessIssuer.issue(any())).thenReturn(accessToken);
        when(refreshService.issue(any())).thenReturn(refreshToken);
        CoreAgentAuthorizationCodeTokenService service = new CoreAgentAuthorizationCodeTokenService(policyResolver, accounts,
                new AccountGrantTypeResolver(AccountGrantTypeResolver.requiredDefaultGrantTypes()), roles, scopes,
                accessIssuer, refreshService);
        return new Fixture(service, policyResolver, accounts, roles, scopes, accessIssuer, refreshService, policy,
                effectiveRole);
    }

    private static AuthorizationAccount account(String username, Long roleId, int status, String extraGrantTypes) {
        return new AuthorizationAccount(7L, username, "$2a$10$" + "a".repeat(53), "alice@example.test", roleId,
                extraGrantTypes, status);
    }

    private static VerifiedCoreAgentAuthorizationCode verified(boolean socketChanged) {
        return new VerifiedCoreAgentAuthorizationCode("registered-core-agent", "core_agent", 7L, "alice", 3L,
                List.of("note:read", "*:super"), AccountGrantTypeResolver.AUTHORIZATION_CODE, socketChanged);
    }

    private record Fixture(CoreAgentAuthorizationCodeTokenService service,
                           CoreAgentRegisteredClientPolicyResolver policyResolver,
                           AuthorizationAccountRepository accounts,
                           EffectiveRolePermissionResolver roles,
                           OAuth2ScopeResolver scopes,
                           Rs256AccessTokenIssuer accessIssuer,
                           OAuth2RefreshTokenSessionService refreshService,
                           CoreAgentRegisteredClientPolicy policy,
                           EffectiveRolePermissions effectiveRole) {
    }
}
