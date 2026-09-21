package com.jacolp.system.application.authorization;

import com.jacolp.constant.UserConstant;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.common.security.oauth2.token.AccessTokenIssueRequest;
import com.jacolp.common.security.oauth2.token.IssuedAccessToken;
import com.jacolp.common.security.oauth2.token.IssuedRefreshToken;
import com.jacolp.common.security.oauth2.token.OAuth2RefreshTokenSessionService;
import com.jacolp.common.security.oauth2.token.Rs256AccessTokenIssuer;
import com.jacolp.common.security.oauth2.token.VerifiedRefreshToken;
import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.InternalIssuedTokens;
import com.jacolp.system.application.authorization.model.InternalRefreshTokenRequest;
import com.jacolp.system.application.authorization.model.InternalRegisteredClientPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InternalRefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");
    private static final String RAW_REFRESH = "A".repeat(43);
    private static final String FINGERPRINT = "B".repeat(43);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ServiceOnlyConfiguration.class);

    @Test
    void reauthorizesCurrentRightsThenRotatesByCompareAndSet() {
        Fixture fixture = fixture(List.of("note:read"));
        when(fixture.refresh.verify(RAW_REFRESH)).thenReturn(Optional.of(verified("user", List.of("note:read"))));
        when(fixture.access.issue(any())).thenReturn(accessToken());
        when(fixture.refresh.rotate(any(), any(), any(), any())).thenReturn(Optional.of(refreshToken()));

        InternalIssuedTokens tokens = fixture.service.refresh(request(null, "192.0.2.7"));

        assertThat(tokens.accessToken()).isEqualTo("access-token");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-token");
        assertThat(tokens.grantedScopes()).containsExactly("note:read");
        ArgumentCaptor<AccessTokenIssueRequest> accessRequest = ArgumentCaptor.forClass(AccessTokenIssueRequest.class);
        verify(fixture.access).issue(accessRequest.capture());
        assertThat(accessRequest.getValue().clientId()).isEqualTo("user");
        assertThat(accessRequest.getValue().grantType()).isEqualTo("refresh_token");
        assertThat(accessRequest.getValue().role()).isEqualTo("USER");
        verify(fixture.refresh).rotate(any(), any(), org.mockito.ArgumentMatchers.eq(List.of("note:read")),
                org.mockito.ArgumentMatchers.eq(Duration.ofHours(24)));
        InOrder order = inOrder(fixture.policy, fixture.refresh, fixture.accounts, fixture.roles, fixture.access);
        order.verify(fixture.policy).resolveRefresh("user");
        order.verify(fixture.refresh).verify(RAW_REFRESH);
        order.verify(fixture.accounts).findById(7L);
        order.verify(fixture.roles).resolve(3L);
        order.verify(fixture.access).issue(any());
        order.verify(fixture.refresh).rotate(any(), any(), any(), any());
    }

    @Test
    void explicitScopeCanOnlyNarrowTheCurrentIntersection() {
        Fixture fixture = fixture(List.of("*:read"));
        when(fixture.refresh.verify(RAW_REFRESH)).thenReturn(Optional.of(verified("user", List.of("*:read"))));
        when(fixture.access.issue(any())).thenReturn(accessToken());
        when(fixture.refresh.rotate(any(), any(), any(), any())).thenReturn(Optional.of(refreshToken()));

        InternalIssuedTokens tokens = fixture.service.refresh(request(List.of("note:read"), "192.0.2.7"));

        assertThat(tokens.grantedScopes()).containsExactly("note:read");
        verify(fixture.refresh).rotate(any(), any(), org.mockito.ArgumentMatchers.eq(List.of("note:read")),
                org.mockito.ArgumentMatchers.eq(Duration.ofHours(24)));
    }

    @Test
    void revalidatesClientIpAndAccountAndAllowsManagementRoleBeforeIssuing() {
        Fixture wrongClient = fixture(List.of("note:read"));
        when(wrongClient.refresh.verify(RAW_REFRESH)).thenReturn(Optional.of(verified("admin", List.of("note:read"))));
        assertRejected(() -> wrongClient.service.refresh(request(null, "192.0.2.7")),
                InternalRefreshTokenRejectedException.MESSAGE);
        verifyNoInteractions(wrongClient.accounts, wrongClient.roles, wrongClient.access);

        Fixture inactive = fixture(List.of("note:read"));
        when(inactive.refresh.verify(RAW_REFRESH)).thenReturn(Optional.of(verified("user", List.of("note:read"))));
        when(inactive.accounts.findById(7L)).thenReturn(Optional.of(account(UserConstant.ACTIVE_STATUS + 1)));
        assertRejected(() -> inactive.service.refresh(request(null, "192.0.2.7")), "账号未激活，请先激活账号");

        Fixture wrongRole = fixture(List.of("note:read"));
        when(wrongRole.refresh.verify(RAW_REFRESH)).thenReturn(Optional.of(verified("user", List.of("note:read"))));
        when(wrongRole.roles.resolve(3L)).thenReturn(new EffectiveRolePermissions(3L, "ADMIN", 2, List.of("note:read")));
        when(wrongRole.access.issue(any())).thenReturn(accessToken());
        when(wrongRole.refresh.rotate(any(), any(), any(), any())).thenReturn(Optional.of(refreshToken()));
        InternalIssuedTokens managementTokens = wrongRole.service.refresh(request(null, "192.0.2.7"));
        assertThat(managementTokens.accessToken()).isEqualTo("access-token");
        ArgumentCaptor<AccessTokenIssueRequest> managementAccessRequest = ArgumentCaptor.forClass(AccessTokenIssueRequest.class);
        verify(wrongRole.access).issue(managementAccessRequest.capture());
        assertThat(managementAccessRequest.getValue().role()).isEqualTo("ADMIN");

        Fixture blockedIp = fixture(List.of("note:read"));
        assertRejected(() -> blockedIp.service.refresh(request(null, "198.51.100.1")),
                "当前 IP 不在允许的登录范围内");
        verifyNoInteractions(blockedIp.refresh, blockedIp.accounts, blockedIp.roles, blockedIp.access);
    }

    @Test
    void revokedScopesAndRefreshCasMissProduceNoSuccessfulResponse() {
        Fixture revoked = fixture(List.of("media:read"));
        when(revoked.refresh.verify(RAW_REFRESH)).thenReturn(Optional.of(verified("user", List.of("note:read"))));
        assertRejected(() -> revoked.service.refresh(request(null, "192.0.2.7")), "当前账号没有可用的访问权限");
        verify(revoked.access, never()).issue(any());

        Fixture casMiss = fixture(List.of("note:read"));
        when(casMiss.refresh.verify(RAW_REFRESH)).thenReturn(Optional.of(verified("user", List.of("note:read"))));
        when(casMiss.access.issue(any())).thenReturn(accessToken());
        when(casMiss.refresh.rotate(any(), any(), any(), any())).thenReturn(Optional.empty());
        assertRejected(() -> casMiss.service.refresh(request(null, "192.0.2.7")),
                InternalRefreshTokenRejectedException.MESSAGE);
    }

    @Test
    void requestIsStrictAndRedactedAndTheServiceIsSpringConstructible() {
        InternalRefreshTokenRequest request = request(List.of("note:read", "*:read"), "192.0.2.7");
        assertThat(request.requestedScopes()).containsExactly("*:read", "note:read");
        assertThat(request.toString()).contains("<redacted>").doesNotContain(RAW_REFRESH, "192.0.2.7", "note:read");
        assertThatIllegalArgumentException().isThrownBy(() -> new InternalRefreshTokenRequest("core_agent", RAW_REFRESH,
                null, "192.0.2.7"));
        assertThatIllegalArgumentException().isThrownBy(() -> new InternalRefreshTokenRequest("user", "bad", null,
                "192.0.2.7"));

        runner.withUserConfiguration(DependencyConfiguration.class)
                .run(context -> assertThat(context.getBeansOfType(InternalRefreshTokenService.class)).hasSize(1));
    }

    private static void assertRejected(org.junit.jupiter.api.function.Executable executable, String message) {
        assertThatThrownBy(() -> executable.execute()).hasMessage(message);
    }

    private static Fixture fixture(List<String> effectivePermissions) {
        InternalRegisteredClientPolicyResolver policy = Mockito.mock(InternalRegisteredClientPolicyResolver.class);
        AuthorizationAccountRepository accounts = mock(AuthorizationAccountRepository.class);
        EffectiveRolePermissionResolver roles = Mockito.mock(EffectiveRolePermissionResolver.class);
        Rs256AccessTokenIssuer access = mock(Rs256AccessTokenIssuer.class);
        OAuth2RefreshTokenSessionService refresh = mock(OAuth2RefreshTokenSessionService.class);
        InternalRegisteredClientPolicy resolvedPolicy = new InternalRegisteredClientPolicy("registered-user", "user",
                "refresh_token", Set.of("*:read"), Set.of("note:read"), "192.0.2.0/24", Duration.ofHours(1),
                Duration.ofHours(24));
        when(policy.resolveRefresh("user")).thenReturn(resolvedPolicy);
        when(accounts.findById(7L)).thenReturn(Optional.of(account(UserConstant.ACTIVE_STATUS)));
        when(roles.resolve(3L)).thenReturn(new EffectiveRolePermissions(3L, "USER", 3, effectivePermissions));
        return new Fixture(new InternalRefreshTokenService(policy, accounts, roles, new OAuth2ScopeResolver(), access, refresh),
                policy, accounts, roles, access, refresh);
    }

    private static AuthorizationAccount account(int status) {
        return new AuthorizationAccount(7L, "alice", "$2a$10$" + "a".repeat(53), "alice@example.test", 3L, "", status);
    }

    private static InternalRefreshTokenRequest request(List<String> requestedScopes, String socketRemoteAddress) {
        return new InternalRefreshTokenRequest("user", RAW_REFRESH, requestedScopes, socketRemoteAddress);
    }

    private static VerifiedRefreshToken verified(String clientId, List<String> scopes) {
        return new VerifiedRefreshToken(FINGERPRINT, 7L, clientId, scopes, NOW.plus(Duration.ofHours(24)));
    }

    private static IssuedAccessToken accessToken() {
        return new IssuedAccessToken("access-token", "A".repeat(22), NOW, NOW.plus(Duration.ofHours(1)));
    }

    private static IssuedRefreshToken refreshToken() {
        return new IssuedRefreshToken("refresh-token", NOW.plusSeconds(1), NOW.plus(Duration.ofHours(24)));
    }

    private record Fixture(InternalRefreshTokenService service, InternalRegisteredClientPolicyResolver policy,
                           AuthorizationAccountRepository accounts, EffectiveRolePermissionResolver roles,
                           Rs256AccessTokenIssuer access, OAuth2RefreshTokenSessionService refresh) {
    }

    @Configuration(proxyBeanMethods = false)
    @Import(InternalRefreshTokenService.class)
    static class ServiceOnlyConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class DependencyConfiguration {
        @Bean InternalRegisteredClientPolicyResolver policyResolver() { return Mockito.mock(InternalRegisteredClientPolicyResolver.class); }
        @Bean AuthorizationAccountRepository accountRepository() { return mock(AuthorizationAccountRepository.class); }
        @Bean EffectiveRolePermissionResolver rolePermissionResolver() { return Mockito.mock(EffectiveRolePermissionResolver.class); }
        @Bean OAuth2ScopeResolver scopeResolver() { return new OAuth2ScopeResolver(); }
        @Bean Rs256AccessTokenIssuer accessTokenIssuer() { return mock(Rs256AccessTokenIssuer.class); }
        @Bean OAuth2RefreshTokenSessionService refreshTokenSessionService() { return mock(OAuth2RefreshTokenSessionService.class); }
    }
}
