package com.jacolp.system.application.authorization;

import com.jacolp.common.security.context.CurrentAccessTokenAccessor;
import com.jacolp.common.security.context.CurrentAccessTokenReference;
import com.jacolp.common.security.oauth2.token.OAuth2SessionRevocationRequest;
import com.jacolp.common.security.oauth2.token.OAuth2SessionRevocationStore;
import com.jacolp.common.security.oauth2.token.OAuth2SessionState;
import com.jacolp.common.security.oauth2.token.OAuth2TokenStateStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalLogoutServiceTest {
    private static final Instant EXPIRY = Instant.parse("2026-08-11T01:00:00Z");
    private static final String JTI = "0123456789abcdefghijkl";
    private static final String FP = "0123456789abcdefghijklmnopqrstuvwxyzaBcDeFg";

    @Test
    void revokesCurrentSessionWithItsRefreshFingerprint() {
        Fixture fixture = fixture("user");
        when(fixture.sessions.findSession("user", 7L)).thenReturn(Optional.of(session("user", 7L)));
        when(fixture.revocations.revoke(any())).thenReturn(true);

        fixture.service.logout();

        ArgumentCaptor<OAuth2SessionRevocationRequest> request = ArgumentCaptor.forClass(OAuth2SessionRevocationRequest.class);
        verify(fixture.revocations).revoke(request.capture());
        assertThat(request.getValue().refreshFingerprint()).isEqualTo(FP);
        assertThat(request.getValue().accessJti()).isEqualTo(JTI);
    }

    @Test
    void missingSessionRevokesOnlyTheAccessToken() {
        Fixture fixture = fixture("admin");
        when(fixture.sessions.findSession("admin", 7L)).thenReturn(Optional.empty());
        when(fixture.revocations.revoke(any())).thenReturn(true);
        fixture.service.logout();
        ArgumentCaptor<OAuth2SessionRevocationRequest> request = ArgumentCaptor.forClass(OAuth2SessionRevocationRequest.class);
        verify(fixture.revocations).revoke(request.capture());
        assertThat(request.getValue().refreshFingerprint()).isNull();
    }

    @Test
    void rejectsMissingOrNonInternalAccessTokenWithoutReadingSession() {
        Fixture missing = fixture("user");
        when(missing.access.currentAccessToken()).thenReturn(Optional.empty());
        assertThatThrownBy(missing.service::logout).isInstanceOf(InternalLogoutRejectedException.class);
        verify(missing.sessions, never()).findSession(any(), any(Long.class));

        Fixture core = fixture("core_agent");
        assertThatThrownBy(core.service::logout).isInstanceOf(InternalLogoutRejectedException.class);
        verify(core.sessions, never()).findSession(any(), any(Long.class));
    }

    @Test
    void rejectsPollutedSessionAndRetriesStaleCasAtMostThreeTimes() {
        Fixture polluted = fixture("user");
        when(polluted.sessions.findSession("user", 7L)).thenReturn(Optional.of(session("admin", 7L)));
        assertThatThrownBy(polluted.service::logout).isInstanceOf(IllegalStateException.class);
        verify(polluted.revocations, never()).revoke(any());

        Fixture retry = fixture("user");
        String nextFingerprint = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO01";
        when(retry.sessions.findSession("user", 7L)).thenReturn(
                Optional.of(session("user", 7L)),
                Optional.of(new OAuth2SessionState(7L, "user", JTI, EXPIRY, nextFingerprint, EXPIRY.plusSeconds(60))));
        when(retry.revocations.revoke(any())).thenReturn(false, true);
        retry.service.logout();
        verify(retry.sessions, times(2)).findSession("user", 7L);
        verify(retry.revocations, times(2)).revoke(any());
        ArgumentCaptor<OAuth2SessionRevocationRequest> retries = ArgumentCaptor.forClass(OAuth2SessionRevocationRequest.class);
        verify(retry.revocations, times(2)).revoke(retries.capture());
        assertThat(retries.getAllValues().stream().map(OAuth2SessionRevocationRequest::refreshFingerprint))
                .containsExactly(FP, nextFingerprint);

        Fixture exhausted = fixture("user");
        when(exhausted.sessions.findSession("user", 7L)).thenReturn(Optional.empty());
        when(exhausted.revocations.revoke(any())).thenReturn(false);
        assertThatThrownBy(exhausted.service::logout).isInstanceOf(IllegalStateException.class);
        verify(exhausted.revocations, times(3)).revoke(any());
    }

    @Test
    void nullOptionalAndDependencyFailuresPropagateFailClosed() {
        Fixture nullLookup = fixture("user");
        when(nullLookup.sessions.findSession("user", 7L)).thenReturn(null);
        assertThatThrownBy(nullLookup.service::logout).isInstanceOf(IllegalStateException.class);

        Fixture failure = fixture("user");
        IllegalStateException redisFailure = new IllegalStateException("redis");
        when(failure.sessions.findSession("user", 7L)).thenThrow(redisFailure);
        assertThatThrownBy(failure.service::logout).isSameAs(redisFailure);
    }

    private static Fixture fixture(String clientId) {
        CurrentAccessTokenAccessor access = mock(CurrentAccessTokenAccessor.class);
        OAuth2TokenStateStore sessions = mock(OAuth2TokenStateStore.class);
        OAuth2SessionRevocationStore revocations = mock(OAuth2SessionRevocationStore.class);
        when(access.currentAccessToken()).thenReturn(Optional.of(new CurrentAccessTokenReference(7, clientId, JTI, EXPIRY)));
        return new Fixture(access, sessions, revocations, new InternalLogoutService(access, sessions, revocations));
    }

    private static OAuth2SessionState session(String clientId, long userId) {
        return new OAuth2SessionState(userId, clientId, JTI, EXPIRY, FP, EXPIRY.plusSeconds(60));
    }

    private record Fixture(CurrentAccessTokenAccessor access, OAuth2TokenStateStore sessions,
                           OAuth2SessionRevocationStore revocations, InternalLogoutService service) {
    }
}
