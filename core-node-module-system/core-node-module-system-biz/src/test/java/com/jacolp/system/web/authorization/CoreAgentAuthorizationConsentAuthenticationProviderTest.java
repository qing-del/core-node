package com.jacolp.system.web.authorization;

import com.jacolp.system.application.port.out.CoreAgentPendingAuthorizationStore;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationCodeIssueRejectedException;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationCodeIssueService;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationConsentService;
import com.jacolp.system.application.authorization.CoreAgentBrowserAuthenticationToken;
import com.jacolp.system.application.authorization.CoreAgentRegisteredClientPolicyResolver;
import com.jacolp.system.application.authorization.EffectiveRolePermissionResolver;
import com.jacolp.system.application.authorization.model.CoreAgentBrowserPrincipal;
import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationConversionRequest;
import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationState;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationCode;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationPendingHandle;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationToken;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreAgentAuthorizationConsentAuthenticationProviderTest {

    private static final Instant NOW = Instant.parse("2026-08-11T08:30:00Z");
    private static final String REDIRECT_URI = "http://127.0.0.1:9090/oauth/callback";
    private static final String STATE = "client-state";
    private static final String HANDLE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String RAW_CODE = HANDLE;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProviderOnlyConfiguration.class);

    @Test
    void approvesOnlySubmittedOptionalScopesPersistsConsentThenReturnsStandardTrustedCodeToken() {
        Fixture fixture = fixture();
        retainHandle(fixture);
        when(fixture.pendingStore.find(HANDLE)).thenReturn(Optional.of(pending(fixture.session)));
        when(fixture.consentService.confirm(eq(7L), any(), any(), eq(List.of("note:read", "note:write")),
                eq(List.of("note:write")))).thenReturn(List.of("note:read", "note:write"));
        when(fixture.issueService.convertPending(any())).thenReturn(new IssuedCoreAgentAuthorizationCode(RAW_CODE,
                NOW.plus(Duration.ofMinutes(10))));

        Authentication result = fixture.provider.authenticate(request(fixture.session,
                CoreAgentAuthorizationEndpointRequestDetails.ConsentAction.APPROVE, Set.of("note:write")));

        assertThat(result).isInstanceOf(OAuth2AuthorizationCodeRequestAuthenticationToken.class);
        OAuth2AuthorizationCodeRequestAuthenticationToken success =
                (OAuth2AuthorizationCodeRequestAuthenticationToken) result;
        assertThat(success.isAuthenticated()).isTrue();
        assertThat(success.getAuthorizationCode().getTokenValue()).isEqualTo(RAW_CODE);
        assertThat(success.getAuthorizationCode().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(success.getClientId()).isEqualTo("core_agent");
        assertThat(success.getRedirectUri()).isEqualTo(REDIRECT_URI);
        assertThat(success.getState()).isEqualTo(STATE);
        assertThat(success.getScopes()).containsExactly("note:read", "note:write");
        assertThat(fixture.handleStore.find(fixture.session)).isEmpty();

        org.mockito.ArgumentCaptor<CoreAgentPendingAuthorizationConversionRequest> conversion =
                org.mockito.ArgumentCaptor.forClass(CoreAgentPendingAuthorizationConversionRequest.class);
        verify(fixture.issueService).convertPending(conversion.capture());
        assertThat(conversion.getValue().rawPendingHandle()).isEqualTo(HANDLE);
        assertThat(conversion.getValue().authenticatedUserId()).isEqualTo(7L);
        assertThat(conversion.getValue().sessionId()).isEqualTo(fixture.session.getId());
        assertThat(conversion.getValue().clientId()).isEqualTo("core_agent");
        assertThat(conversion.getValue().redirectUri()).isEqualTo(REDIRECT_URI);
        assertThat(conversion.getValue().oauthState()).isEqualTo(STATE);
        assertThat(conversion.getValue().grantedScopes()).containsExactly("note:read", "note:write");
        org.mockito.InOrder order = inOrder(fixture.consentService, fixture.issueService);
        order.verify(fixture.consentService).confirm(any(), any(), any(), any(), any());
        order.verify(fixture.issueService).convertPending(any());
    }

    @Test
    void approvesEmptyOptionalSelectionWhenMandatoryScopesRemain() {
        Fixture fixture = fixture();
        retainHandle(fixture);
        when(fixture.pendingStore.find(HANDLE)).thenReturn(Optional.of(pending(fixture.session)));
        when(fixture.consentService.confirm(any(), any(), any(), any(), eq(List.of())))
                .thenReturn(List.of("note:read"));
        when(fixture.issueService.convertPending(any())).thenReturn(new IssuedCoreAgentAuthorizationCode(RAW_CODE,
                NOW.plus(Duration.ofMinutes(10))));

        OAuth2AuthorizationCodeRequestAuthenticationToken result =
                (OAuth2AuthorizationCodeRequestAuthenticationToken) fixture.provider.authenticate(request(fixture.session,
                        CoreAgentAuthorizationEndpointRequestDetails.ConsentAction.APPROVE, Set.of()));

        assertThat(result.getScopes()).containsExactly("note:read");
    }

    @Test
    void denyDeletesTrustedPendingAndReturnsBoundAccessDeniedWithoutSavingOrIssuing() {
        Fixture fixture = fixture();
        retainHandle(fixture);
        when(fixture.pendingStore.find(HANDLE)).thenReturn(Optional.of(pending(fixture.session)));

        OAuth2AuthorizationCodeRequestAuthenticationException exception = assertBoundAccessDenied(
                () -> fixture.provider.authenticate(request(fixture.session,
                        CoreAgentAuthorizationEndpointRequestDetails.ConsentAction.DENY, Set.of("note:write"))));

        assertThat(exception.getAuthorizationCodeRequestAuthentication().getRedirectUri()).isEqualTo(REDIRECT_URI);
        assertThat(exception.getAuthorizationCodeRequestAuthentication().getState()).isEqualTo(STATE);
        verify(fixture.pendingStore).delete(HANDLE);
        verify(fixture.consentService, never()).confirm(any(), any(), any(), any(), any());
        verify(fixture.issueService, never()).convertPending(any());
        assertThat(fixture.handleStore.find(fixture.session)).isEmpty();
    }

    @Test
    void denyKeepsTheHandleWhenRedisDeletionFails() {
        Fixture fixture = fixture();
        retainHandle(fixture);
        when(fixture.pendingStore.find(HANDLE)).thenReturn(Optional.of(pending(fixture.session)));
        doThrow(new IllegalStateException("redis unavailable")).when(fixture.pendingStore).delete(HANDLE);

        assertThatIllegalStateException().isThrownBy(() -> fixture.provider.authenticate(request(fixture.session,
                CoreAgentAuthorizationEndpointRequestDetails.ConsentAction.DENY, Set.of())))
                .withMessage("redis unavailable");

        assertThat(fixture.handleStore.find(fixture.session)).contains(HANDLE);
        verify(fixture.consentService, never()).confirm(any(), any(), any(), any(), any());
        verify(fixture.issueService, never()).convertPending(any());
    }

    @Test
    void dbConsentFailureKeepsPendingAndHandleForRetry() {
        Fixture fixture = fixture();
        retainHandle(fixture);
        when(fixture.pendingStore.find(HANDLE)).thenReturn(Optional.of(pending(fixture.session)));
        when(fixture.consentService.confirm(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatIllegalStateException().isThrownBy(() -> fixture.provider.authenticate(request(fixture.session,
                CoreAgentAuthorizationEndpointRequestDetails.ConsentAction.APPROVE, Set.of("note:write"))))
                .withMessage("database unavailable");

        assertThat(fixture.handleStore.find(fixture.session)).contains(HANDLE);
        verify(fixture.pendingStore, never()).delete(HANDLE);
        verify(fixture.issueService, never()).convertPending(any());
    }

    @Test
    void transitionFailureKeepsPendingAndHandleForRetry() {
        Fixture fixture = fixture();
        retainHandle(fixture);
        when(fixture.pendingStore.find(HANDLE)).thenReturn(Optional.of(pending(fixture.session)));
        when(fixture.consentService.confirm(any(), any(), any(), any(), any()))
                .thenReturn(List.of("note:read", "note:write"));
        when(fixture.issueService.convertPending(any())).thenThrow(new CoreAgentAuthorizationCodeIssueRejectedException());

        assertBoundAccessDenied(() -> fixture.provider.authenticate(request(fixture.session,
                CoreAgentAuthorizationEndpointRequestDetails.ConsentAction.APPROVE, Set.of("note:write"))));

        assertThat(fixture.handleStore.find(fixture.session)).contains(HANDLE);
    }

    @Test
    void successfulTransitionRemainsSuccessfulWhenSessionCleanupFailsAndReplayCannotIssueAnotherCode() {
        Fixture base = fixture();
        HttpSessionCoreAgentPendingAuthorizationHandleStore failingHandleStore =
                mock(HttpSessionCoreAgentPendingAuthorizationHandleStore.class);
        when(failingHandleStore.find(base.session)).thenReturn(Optional.of(HANDLE));
        doThrow(new IllegalStateException("session cleanup unavailable")).when(failingHandleStore)
                .removeIfMatches(base.session, HANDLE);
        Fixture fixture = base.withHandleStore(failingHandleStore);
        when(fixture.pendingStore.find(HANDLE)).thenReturn(Optional.of(pending(fixture.session)), Optional.empty());
        when(fixture.consentService.confirm(any(), any(), any(), any(), any()))
                .thenReturn(List.of("note:read", "note:write"));
        when(fixture.issueService.convertPending(any())).thenReturn(new IssuedCoreAgentAuthorizationCode(RAW_CODE,
                NOW.plus(Duration.ofMinutes(10))));

        Authentication success = fixture.provider.authenticate(request(fixture.session,
                CoreAgentAuthorizationEndpointRequestDetails.ConsentAction.APPROVE, Set.of("note:write")));
        assertThat(success).isInstanceOf(OAuth2AuthorizationCodeRequestAuthenticationToken.class);
        assertUnboundInvalid(() -> fixture.provider.authenticate(request(fixture.session,
                CoreAgentAuthorizationEndpointRequestDetails.ConsentAction.APPROVE, Set.of("note:write"))));

        verify(fixture.issueService).convertPending(any());
    }

    @Test
    void rejectsMissingOrMismatchedBindingsWithoutAClientRedirect() {
        Fixture missing = fixture();
        assertUnboundInvalid(() -> missing.provider.authenticate(request(missing.session,
                CoreAgentAuthorizationEndpointRequestDetails.ConsentAction.APPROVE, Set.of("note:write"))));

        Fixture mismatch = fixture();
        retainHandle(mismatch);
        CoreAgentPendingAuthorizationState wrongState = new CoreAgentPendingAuthorizationState("core_agent", REDIRECT_URI,
                List.of("note:read", "note:write"), HANDLE, "S256", "other-state", "127.0.0.1", 7L,
                mismatch.session.getId(), NOW, NOW.plus(Duration.ofMinutes(10)));
        when(mismatch.pendingStore.find(HANDLE)).thenReturn(Optional.of(wrongState));

        assertUnboundInvalid(() -> mismatch.provider.authenticate(request(mismatch.session,
                CoreAgentAuthorizationEndpointRequestDetails.ConsentAction.APPROVE, Set.of("note:write"))));
        verify(mismatch.consentService, never()).confirm(any(), any(), any(), any(), any());
        verify(mismatch.issueService, never()).convertPending(any());
    }

    @Test
    void requiresOfficialConsentTokenTrustedDetailsAndBrowserPrincipalAndIsAlwaysRegistered() {
        Fixture fixture = fixture();
        assertThat(fixture.provider.supports(OAuth2AuthorizationConsentAuthenticationToken.class)).isTrue();
        assertThat(fixture.provider.supports(OAuth2AuthorizationCodeRequestAuthenticationToken.class)).isFalse();
        assertThat(fixture.provider.supports(UsernamePasswordAuthenticationToken.class)).isFalse();

        OAuth2AuthorizationConsentAuthenticationToken noDetails = request(fixture.session,
                CoreAgentAuthorizationEndpointRequestDetails.ConsentAction.APPROVE, Set.of());
        noDetails.setDetails(null);
        assertUnboundInvalid(() -> fixture.provider.authenticate(noDetails));
        OAuth2AuthorizationConsentAuthenticationToken wrongPrincipal = new OAuth2AuthorizationConsentAuthenticationToken(
                "http://core-node.test/oauth2/authorize", "core_agent",
                UsernamePasswordAuthenticationToken.authenticated("7", null, List.of()), STATE, Set.of(), Map.of());
        wrongPrincipal.setDetails(details(fixture.session,
                CoreAgentAuthorizationEndpointRequestDetails.ConsentAction.APPROVE));
        assertUnboundInvalid(() -> fixture.provider.authenticate(wrongPrincipal));

        runner.withUserConfiguration(DependencyConfiguration.class)
                .run(context -> assertThat(context.getBeansOfType(
                        CoreAgentAuthorizationConsentAuthenticationProvider.class)).hasSize(1));
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationException assertBoundAccessDenied(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return assertOAuthError(callable, "access_denied", false);
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationException assertUnboundInvalid(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return assertOAuthError(callable, "invalid_request", true);
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationException assertOAuthError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code, boolean noRequestToken) {
        Throwable thrown = catchThrowable(callable);
        assertThat(thrown).isInstanceOf(OAuth2AuthorizationCodeRequestAuthenticationException.class);
        OAuth2AuthorizationCodeRequestAuthenticationException exception =
                (OAuth2AuthorizationCodeRequestAuthenticationException) thrown;
        assertThat(exception.getError().getErrorCode()).isEqualTo(code);
        if (noRequestToken) {
            assertThat(exception.getAuthorizationCodeRequestAuthentication()).isNull();
        } else {
            assertThat(exception.getAuthorizationCodeRequestAuthentication()).isNotNull();
        }
        return exception;
    }

    private static Fixture fixture() {
        CoreAgentRegisteredClientPolicyResolver policyResolver = mock(CoreAgentRegisteredClientPolicyResolver.class);
        when(policyResolver.resolve(CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID)).thenReturn(policy());
        EffectiveRolePermissionResolver roleResolver = mock(EffectiveRolePermissionResolver.class);
        when(roleResolver.resolve(2L)).thenReturn(role());
        CoreAgentAuthorizationConsentService consentService = mock(CoreAgentAuthorizationConsentService.class);
        CoreAgentAuthorizationCodeIssueService issueService = mock(CoreAgentAuthorizationCodeIssueService.class);
        HttpSessionCoreAgentPendingAuthorizationHandleStore handleStore =
                new HttpSessionCoreAgentPendingAuthorizationHandleStore(Clock.fixed(NOW, ZoneOffset.UTC));
        CoreAgentPendingAuthorizationStore pendingStore = mock(CoreAgentPendingAuthorizationStore.class);
        CoreAgentAuthorizationConsentAuthenticationProvider provider =
                new CoreAgentAuthorizationConsentAuthenticationProvider(policyResolver, roleResolver, consentService,
                        issueService, handleStore, pendingStore);
        return new Fixture(provider, policyResolver, roleResolver, consentService, issueService, handleStore, pendingStore,
                new MockHttpSession());
    }

    private static void retainHandle(Fixture fixture) {
        fixture.handleStore.replace(fixture.session, pendingHandle());
    }

    private static OAuth2AuthorizationConsentAuthenticationToken request(HttpSession session,
                                                                           CoreAgentAuthorizationEndpointRequestDetails.ConsentAction action,
                                                                           Set<String> optionalScopes) {
        OAuth2AuthorizationConsentAuthenticationToken request = new OAuth2AuthorizationConsentAuthenticationToken(
                "http://core-node.test/oauth2/authorize", "core_agent",
                CoreAgentBrowserAuthenticationToken.authenticated(principal()), STATE,
                new LinkedHashSet<>(optionalScopes), Map.of());
        request.setDetails(details(session, action));
        return request;
    }

    private static CoreAgentAuthorizationEndpointRequestDetails details(
            HttpSession session, CoreAgentAuthorizationEndpointRequestDetails.ConsentAction action) {
        return new CoreAgentAuthorizationEndpointRequestDetails(session, session.getId(), "127.0.0.1", true, action);
    }

    private static CoreAgentPendingAuthorizationState pending(HttpSession session) {
        return new CoreAgentPendingAuthorizationState("core_agent", REDIRECT_URI, List.of("note:read", "note:write"),
                HANDLE, "S256", STATE, "127.0.0.1", 7L, session.getId(), NOW, NOW.plus(Duration.ofMinutes(10)));
    }

    private static IssuedCoreAgentAuthorizationPendingHandle pendingHandle() {
        return new IssuedCoreAgentAuthorizationPendingHandle(HANDLE, NOW.plus(Duration.ofMinutes(10)));
    }

    private static CoreAgentRegisteredClientPolicy policy() {
        return new CoreAgentRegisteredClientPolicy("registered-core-agent", "core_agent", REDIRECT_URI,
                Set.of("note:read", "note:write"), Set.of("note:read"), "127.0.0.1/32",
                Duration.ofHours(1), Duration.ofHours(24), Duration.ofMinutes(10));
    }

    private static EffectiveRolePermissions role() {
        return new EffectiveRolePermissions(2L, "USER", 2, List.of("note:read", "note:write"));
    }

    private static CoreAgentBrowserPrincipal principal() {
        return new CoreAgentBrowserPrincipal(7L, "alice", 2L, "USER", 2);
    }

    private record Fixture(CoreAgentAuthorizationConsentAuthenticationProvider provider,
                           CoreAgentRegisteredClientPolicyResolver policyResolver,
                           EffectiveRolePermissionResolver roleResolver,
                           CoreAgentAuthorizationConsentService consentService,
                           CoreAgentAuthorizationCodeIssueService issueService,
                           HttpSessionCoreAgentPendingAuthorizationHandleStore handleStore,
                           CoreAgentPendingAuthorizationStore pendingStore,
                           MockHttpSession session) {

        private Fixture withHandleStore(HttpSessionCoreAgentPendingAuthorizationHandleStore replacement) {
            return new Fixture(new CoreAgentAuthorizationConsentAuthenticationProvider(policyResolver, roleResolver,
                    consentService, issueService, replacement, pendingStore), policyResolver, roleResolver, consentService,
                    issueService, replacement, pendingStore, session);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentAuthorizationConsentAuthenticationProvider.class)
    static class ProviderOnlyConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class DependencyConfiguration {
        @Bean CoreAgentRegisteredClientPolicyResolver policyResolver() {
            return mock(CoreAgentRegisteredClientPolicyResolver.class);
        }

        @Bean EffectiveRolePermissionResolver roleResolver() {
            return mock(EffectiveRolePermissionResolver.class);
        }

        @Bean CoreAgentAuthorizationConsentService consentService() {
            return mock(CoreAgentAuthorizationConsentService.class);
        }

        @Bean CoreAgentAuthorizationCodeIssueService issueService() {
            return mock(CoreAgentAuthorizationCodeIssueService.class);
        }

        @Bean HttpSessionCoreAgentPendingAuthorizationHandleStore pendingHandleStore() {
            return new HttpSessionCoreAgentPendingAuthorizationHandleStore(Clock.fixed(NOW, ZoneOffset.UTC));
        }

        @Bean CoreAgentPendingAuthorizationStore pendingAuthorizationStore() {
            return mock(CoreAgentPendingAuthorizationStore.class);
        }
    }
}
