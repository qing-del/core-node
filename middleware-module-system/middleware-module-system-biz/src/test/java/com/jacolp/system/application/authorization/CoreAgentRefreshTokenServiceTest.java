package com.jacolp.system.application.authorization;

import com.jacolp.common.security.oauth2.token.*;
import com.jacolp.constant.UserConstant;
import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.authorization.model.CoreAgentRefreshTokenRequest;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentRefreshTokens;
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
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CoreAgentRefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T13:00:00Z");
    private static final String RAW_REFRESH = "A".repeat(43);
    private static final String FINGERPRINT = "B".repeat(43);
    private static final String REDIRECT_URI = "http://127.0.0.1:9090/oauth/callback";
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ServiceOnlyConfiguration.class);

    @Test
    void missingScopeReauthorizesTheCurrentIntersectionThenRotatesWithPolicyTtls() {
        Fixture fixture = fixture(List.of("*:read"));
        when(fixture.refresh.verify(RAW_REFRESH)).thenReturn(Optional.of(verified("core_agent", List.of("*:read"))));
        when(fixture.access.issue(any())).thenReturn(accessToken());
        when(fixture.refresh.rotate(any(), any(), any(), any())).thenReturn(Optional.of(refreshToken()));

        IssuedCoreAgentRefreshTokens tokens = fixture.service.refresh(request(null, "192.0.2.7"));

        assertThat(tokens.accessToken()).isEqualTo("access-token");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-token");
        assertThat(tokens.tokenType()).isEqualTo("Bearer");
        assertThat(tokens.accessIssuedAt()).isEqualTo(NOW);
        assertThat(tokens.accessExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(tokens.refreshIssuedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(tokens.refreshExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(tokens.grantedScopes()).containsExactly("*:read");

        ArgumentCaptor<AccessTokenIssueRequest> accessRequest = ArgumentCaptor.forClass(AccessTokenIssueRequest.class);
        verify(fixture.access).issue(accessRequest.capture());
        assertThat(accessRequest.getValue().grantType()).isEqualTo("refresh_token");
        assertThat(accessRequest.getValue().scopes()).containsExactly("*:read");
        assertThat(accessRequest.getValue().tokenTtl()).isEqualTo(Duration.ofHours(1));
        verify(fixture.refresh).rotate(RAW_REFRESH, new AccessTokenSessionReference(
                "A".repeat(22), NOW.plus(Duration.ofHours(1))), List.of("*:read"), Duration.ofHours(24));
        InOrder order = inOrder(fixture.policyResolver, fixture.refresh, fixture.accounts, fixture.roles, fixture.access);
        order.verify(fixture.policyResolver).resolve("core_agent");
        order.verify(fixture.refresh).verify(RAW_REFRESH);
        order.verify(fixture.accounts).findById(7L);
        order.verify(fixture.roles).resolve(3L);
        order.verify(fixture.access).issue(any());
        order.verify(fixture.refresh).rotate(any(), any(), any(), any());
    }

    @Test
    void explicitScopeRequestNarrowsWildcardsBeforeIssuingAndRotating() {
        Fixture fixture = fixture(List.of("*:read"));
        when(fixture.refresh.verify(RAW_REFRESH)).thenReturn(Optional.of(verified("core_agent", List.of("*:read"))));
        when(fixture.access.issue(any())).thenReturn(accessToken());
        when(fixture.refresh.rotate(any(), any(), any(), any())).thenReturn(Optional.of(refreshToken()));

        IssuedCoreAgentRefreshTokens tokens = fixture.service.refresh(request(List.of("note:read"), "192.0.2.7"));

        assertThat(tokens.grantedScopes()).containsExactly("note:read");
        verify(fixture.refresh).rotate(any(), any(), org.mockito.ArgumentMatchers.eq(List.of("note:read")),
                org.mockito.ArgumentMatchers.eq(Duration.ofHours(24)));
    }

    @Test
    void permissionRevocationAndExplicitEmptyScopeRejectWithoutIssuingAccess() {
        Fixture revoked = fixture(List.of("media:read"));
        when(revoked.refresh.verify(RAW_REFRESH)).thenReturn(Optional.of(verified("core_agent", List.of("note:read"))));
        assertRejected(() -> revoked.service.refresh(request(null, "192.0.2.7")));
        verify(revoked.access, never()).issue(any());

        Fixture empty = fixture(List.of("*:read"));
        when(empty.refresh.verify(RAW_REFRESH)).thenReturn(Optional.of(verified("core_agent", List.of("*:read"))));
        assertRejected(() -> empty.service.refresh(request(List.of(), "192.0.2.7")));
        verify(empty.access, never()).issue(any());
    }

    @Test
    void rejectsWrongRefreshClientAccountGrantRoleAndSocketBeforeIssuing() {
        Fixture wrongClient = fixture(List.of("*:read"));
        when(wrongClient.refresh.verify(RAW_REFRESH)).thenReturn(Optional.of(verified("other", List.of("*:read"))));
        assertRejected(() -> wrongClient.service.refresh(request(null, "192.0.2.7")));
        verifyNoInteractions(wrongClient.accounts, wrongClient.roles, wrongClient.access);

        Fixture missingAccount = fixture(List.of("*:read"));
        when(missingAccount.refresh.verify(RAW_REFRESH)).thenReturn(Optional.of(verified("core_agent", List.of("*:read"))));
        when(missingAccount.accounts.findById(7L)).thenReturn(Optional.empty());
        assertRejected(() -> missingAccount.service.refresh(request(null, "192.0.2.7")));

        Fixture inactive = fixture(List.of("*:read"));
        when(inactive.refresh.verify(RAW_REFRESH)).thenReturn(Optional.of(verified("core_agent", List.of("*:read"))));
        when(inactive.accounts.findById(7L)).thenReturn(Optional.of(account(UserConstant.ACTIVE_STATUS + 1, "")));
        assertRejected(() -> inactive.service.refresh(request(null, "192.0.2.7")));

        Fixture grantPollution = fixture(List.of("*:read"));
        when(grantPollution.refresh.verify(RAW_REFRESH)).thenReturn(Optional.of(verified("core_agent", List.of("*:read"))));
        when(grantPollution.accounts.findById(7L)).thenReturn(Optional.of(account(UserConstant.ACTIVE_STATUS,
                "authorization_code")));
        assertThatIllegalStateException().isThrownBy(() -> grantPollution.service.refresh(request(null, "192.0.2.7")));

        Fixture badRole = fixture(List.of("*:read"));
        when(badRole.refresh.verify(RAW_REFRESH)).thenReturn(Optional.of(verified("core_agent", List.of("*:read"))));
        when(badRole.roles.resolve(3L)).thenReturn(new EffectiveRolePermissions(4L, "USER", 3, List.of("*:read")));
        assertThatIllegalStateException().isThrownBy(() -> badRole.service.refresh(request(null, "192.0.2.7")));

        Fixture badIp = fixture(List.of("*:read"));
        assertRejected(() -> badIp.service.refresh(request(null, "198.51.100.1")));
        verifyNoInteractions(badIp.refresh, badIp.accounts, badIp.roles, badIp.access);
    }

    @Test
    void verifyAndCasMissesRejectWithoutReturningPartialTokensWhileSystemFailuresPropagate() {
        Fixture verifyMiss = fixture(List.of("*:read"));
        when(verifyMiss.refresh.verify(RAW_REFRESH)).thenReturn(Optional.empty());
        assertRejected(() -> verifyMiss.service.refresh(request(null, "192.0.2.7")));
        verifyNoInteractions(verifyMiss.accounts, verifyMiss.roles, verifyMiss.access);

        Fixture casMiss = fixture(List.of("*:read"));
        when(casMiss.refresh.verify(RAW_REFRESH)).thenReturn(Optional.of(verified("core_agent", List.of("*:read"))));
        when(casMiss.access.issue(any())).thenReturn(accessToken());
        when(casMiss.refresh.rotate(any(), any(), any(), any())).thenReturn(Optional.empty());
        assertRejected(() -> casMiss.service.refresh(request(null, "192.0.2.7")));

        Fixture issuerFailure = fixture(List.of("*:read"));
        when(issuerFailure.refresh.verify(RAW_REFRESH)).thenReturn(Optional.of(verified("core_agent", List.of("*:read"))));
        IllegalStateException jwtFailure = new IllegalStateException("jwt unavailable");
        when(issuerFailure.access.issue(any())).thenThrow(jwtFailure);
        assertThatThrownBy(() -> issuerFailure.service.refresh(request(null, "192.0.2.7"))).isSameAs(jwtFailure);

        Fixture redisFailure = fixture(List.of("*:read"));
        IllegalStateException redisException = new IllegalStateException("redis unavailable");
        when(redisFailure.refresh.verify(RAW_REFRESH)).thenThrow(redisException);
        assertThatThrownBy(() -> redisFailure.service.refresh(request(null, "192.0.2.7"))).isSameAs(redisException);
    }

    @Test
    void requestAndOutputModelsAreStrictAndRedacted() {
        CoreAgentRefreshTokenRequest request = request(List.of("note:read", "*:read"), "192.0.2.7");
        assertThat(request.requestedScopes()).containsExactly("*:read", "note:read");
        assertThat(request.toString()).contains("<redacted>").doesNotContain(RAW_REFRESH, "192.0.2.7", "note:read");
        assertThatIllegalArgumentException().isThrownBy(() -> new CoreAgentRefreshTokenRequest("other", RAW_REFRESH,
                null, "192.0.2.7"));
        assertThatIllegalArgumentException().isThrownBy(() -> new CoreAgentRefreshTokenRequest("core_agent", "bad",
                null, "192.0.2.7"));

        IssuedCoreAgentRefreshTokens output = new IssuedCoreAgentRefreshTokens("access-secret", "refresh-secret",
                "Bearer", NOW, NOW.plusSeconds(2), NOW.plusSeconds(1), NOW.plusSeconds(3), List.of("note:read"));
        assertThat(output.toString()).contains("<redacted>").doesNotContain("access-secret", "refresh-secret");
    }

    @Test
    void registersTheService() {
        runner.withUserConfiguration(DependencyConfiguration.class)
                .run(context -> assertThat(context.getBeansOfType(CoreAgentRefreshTokenService.class)).hasSize(1));
    }

    private static void assertRejected(org.junit.jupiter.api.function.Executable executable) {
        assertThatThrownBy(() -> executable.execute()).isInstanceOf(CoreAgentRefreshTokenRejectedException.class)
                .hasMessage(CoreAgentRefreshTokenRejectedException.MESSAGE);
    }

    private static Fixture fixture(List<String> effectivePermissions) {
        CoreAgentRegisteredClientPolicyResolver policyResolver = Mockito.mock(CoreAgentRegisteredClientPolicyResolver.class);
        AuthorizationAccountRepository accounts = mock(AuthorizationAccountRepository.class);
        EffectiveRolePermissionResolver roles = Mockito.mock(EffectiveRolePermissionResolver.class);
        Rs256AccessTokenIssuer access = mock(Rs256AccessTokenIssuer.class);
        OAuth2RefreshTokenSessionService refresh = mock(OAuth2RefreshTokenSessionService.class);
        CoreAgentRegisteredClientPolicy policy = new CoreAgentRegisteredClientPolicy("registered-core-agent", "core_agent",
                REDIRECT_URI, Set.of("*:read"), Set.of("note:read"), "192.0.2.0/24", Duration.ofHours(1),
                Duration.ofHours(24), Duration.ofMinutes(10));
        when(policyResolver.resolve("core_agent")).thenReturn(policy);
        when(accounts.findById(7L)).thenReturn(Optional.of(account(UserConstant.ACTIVE_STATUS, "")));
        when(roles.resolve(3L)).thenReturn(new EffectiveRolePermissions(3L, "USER", 3, effectivePermissions));
        CoreAgentRefreshTokenService service = new CoreAgentRefreshTokenService(policyResolver, accounts,
                new AccountGrantTypeResolver(AccountGrantTypeResolver.requiredDefaultGrantTypes()), roles,
                new OAuth2ScopeResolver(), access, refresh);
        return new Fixture(service, policyResolver, accounts, roles, access, refresh);
    }

    private static AuthorizationAccount account(int status, String extraGrantTypes) {
        return new AuthorizationAccount(7L, "alice", "$2a$10$" + "a".repeat(53), "alice@example.test", 3L,
                extraGrantTypes, status);
    }

    private static CoreAgentRefreshTokenRequest request(List<String> requestedScopes, String socketRemoteAddress) {
        return new CoreAgentRefreshTokenRequest("core_agent", RAW_REFRESH, requestedScopes, socketRemoteAddress);
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

    private record Fixture(CoreAgentRefreshTokenService service,
                           CoreAgentRegisteredClientPolicyResolver policyResolver,
                           AuthorizationAccountRepository accounts,
                           EffectiveRolePermissionResolver roles,
                           Rs256AccessTokenIssuer access,
                           OAuth2RefreshTokenSessionService refresh) {
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentRefreshTokenService.class)
    static class ServiceOnlyConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class DependencyConfiguration {
        @Bean CoreAgentRegisteredClientPolicyResolver policyResolver() { return Mockito.mock(CoreAgentRegisteredClientPolicyResolver.class); }
        @Bean AuthorizationAccountRepository accountRepository() { return mock(AuthorizationAccountRepository.class); }
        @Bean AccountGrantTypeResolver accountGrantTypeResolver() { return new AccountGrantTypeResolver(AccountGrantTypeResolver.requiredDefaultGrantTypes()); }
        @Bean EffectiveRolePermissionResolver rolePermissionResolver() { return Mockito.mock(EffectiveRolePermissionResolver.class); }
        @Bean OAuth2ScopeResolver scopeResolver() { return Mockito.mock(OAuth2ScopeResolver.class); }
        @Bean Rs256AccessTokenIssuer accessTokenIssuer() { return mock(Rs256AccessTokenIssuer.class); }
        @Bean OAuth2RefreshTokenSessionService refreshTokenSessionService() { return mock(OAuth2RefreshTokenSessionService.class); }
    }
}
