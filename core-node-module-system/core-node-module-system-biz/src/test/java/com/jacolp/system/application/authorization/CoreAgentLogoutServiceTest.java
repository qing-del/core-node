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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreAgentLogoutServiceTest {

    private static final Instant EXPIRY = Instant.parse("2026-08-12T01:00:00Z");
    private static final String JTI = "0123456789abcdefghijkl";
    private static final String FINGERPRINT = "0123456789abcdefghijklmnopqrstuvwxyzaBcDeFg";

    @Test
    void revokesCurrentCoreAgentSessionWithCurrentRefreshFingerprint() {
        Fixture fixture = fixture("core_agent");
        when(fixture.sessions.findSession("core_agent", 7L)).thenReturn(Optional.of(session("core_agent", 7L)));
        when(fixture.revocations.revoke(any())).thenReturn(true);

        fixture.service.logout();

        ArgumentCaptor<OAuth2SessionRevocationRequest> request =
                ArgumentCaptor.forClass(OAuth2SessionRevocationRequest.class);
        verify(fixture.revocations).revoke(request.capture());
        assertThat(request.getValue().clientId()).isEqualTo("core_agent");
        assertThat(request.getValue().accessJti()).isEqualTo(JTI);
        assertThat(request.getValue().accessExpiresAt()).isEqualTo(EXPIRY);
        assertThat(request.getValue().refreshFingerprint()).isEqualTo(FINGERPRINT);
    }

    @Test
    void missingSessionStillRevokesTheAccessTokenWithNullFingerprint() {
        Fixture fixture = fixture("core_agent");
        when(fixture.sessions.findSession("core_agent", 7L)).thenReturn(Optional.empty());
        when(fixture.revocations.revoke(any())).thenReturn(true);

        fixture.service.logout();

        ArgumentCaptor<OAuth2SessionRevocationRequest> request =
                ArgumentCaptor.forClass(OAuth2SessionRevocationRequest.class);
        verify(fixture.revocations).revoke(request.capture());
        assertThat(request.getValue().refreshFingerprint()).isNull();
    }

    @Test
    void rejectsAbsentOrWrongClientAccessTokenWithoutReadingSessionOrLeakingIdentity() {
        Fixture absent = fixture("core_agent");
        when(absent.access.currentAccessToken()).thenReturn(Optional.empty());
        assertThatThrownBy(absent.service::logout)
                .isInstanceOf(CoreAgentLogoutRejectedException.class)
                .hasMessage(CoreAgentLogoutRejectedException.MESSAGE);
        assertThat(CoreAgentLogoutRejectedException.MESSAGE)
                .doesNotContain(JTI, "7", "user", "admin");
        verify(absent.sessions, never()).findSession(any(), anyLong());

        Fixture wrongClient = fixture("user");
        assertThatThrownBy(wrongClient.service::logout)
                .isInstanceOf(CoreAgentLogoutRejectedException.class)
                .hasMessage(CoreAgentLogoutRejectedException.MESSAGE);
        verify(wrongClient.sessions, never()).findSession(any(), anyLong());
        verify(wrongClient.revocations, never()).revoke(any());
    }

    @Test
    void failsClosedForPollutedSessionAndRetriesCompareAndSetAtMostThreeTimes() {
        Fixture polluted = fixture("core_agent");
        when(polluted.sessions.findSession("core_agent", 7L)).thenReturn(Optional.of(session("user", 7L)));
        assertThatThrownBy(polluted.service::logout).isInstanceOf(IllegalStateException.class)
                .hasMessage("CORE AGENT logout session identity is inconsistent");
        verify(polluted.revocations, never()).revoke(any());

        Fixture wrongUser = fixture("core_agent");
        when(wrongUser.sessions.findSession("core_agent", 7L)).thenReturn(Optional.of(session("core_agent", 8L)));
        assertThatThrownBy(wrongUser.service::logout).isInstanceOf(IllegalStateException.class)
                .hasMessage("CORE AGENT logout session identity is inconsistent");
        verify(wrongUser.revocations, never()).revoke(any());

        Fixture retried = fixture("core_agent");
        String nextFingerprint = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO01";
        when(retried.sessions.findSession("core_agent", 7L)).thenReturn(Optional.of(session("core_agent", 7L)),
                Optional.of(new OAuth2SessionState(7L, "core_agent", JTI, EXPIRY, nextFingerprint,
                        EXPIRY.plusSeconds(60))));
        when(retried.revocations.revoke(any())).thenReturn(false, true);
        retried.service.logout();
        verify(retried.sessions, times(2)).findSession("core_agent", 7L);
        ArgumentCaptor<OAuth2SessionRevocationRequest> retries =
                ArgumentCaptor.forClass(OAuth2SessionRevocationRequest.class);
        verify(retried.revocations, times(2)).revoke(retries.capture());
        assertThat(retries.getAllValues()).extracting(OAuth2SessionRevocationRequest::refreshFingerprint)
                .containsExactly(FINGERPRINT, nextFingerprint);

        Fixture exhausted = fixture("core_agent");
        when(exhausted.sessions.findSession("core_agent", 7L)).thenReturn(Optional.empty());
        when(exhausted.revocations.revoke(any())).thenReturn(false);
        assertThatThrownBy(exhausted.service::logout).isInstanceOf(IllegalStateException.class)
                .hasMessage("CORE AGENT logout session revocation remained stale");
        verify(exhausted.revocations, times(3)).revoke(any());
    }

    @Test
    void propagatesRedisFailuresAndRejectsNullLookupResult() {
        Fixture nullLookup = fixture("core_agent");
        when(nullLookup.sessions.findSession("core_agent", 7L)).thenReturn(null);
        assertThatThrownBy(nullLookup.service::logout).isInstanceOf(IllegalStateException.class)
                .hasMessage("CORE AGENT logout session lookup returned null");

        Fixture lookupFailure = fixture("core_agent");
        IllegalStateException redisFailure = new IllegalStateException("redis failure");
        when(lookupFailure.sessions.findSession("core_agent", 7L)).thenThrow(redisFailure);
        assertThatThrownBy(lookupFailure.service::logout).isSameAs(redisFailure);

        Fixture revocationFailure = fixture("core_agent");
        when(revocationFailure.sessions.findSession("core_agent", 7L)).thenReturn(Optional.empty());
        when(revocationFailure.revocations.revoke(any())).thenThrow(redisFailure);
        assertThatThrownBy(revocationFailure.service::logout).isSameAs(redisFailure);
    }

    private static Fixture fixture(String clientId) {
        CurrentAccessTokenAccessor access = mock(CurrentAccessTokenAccessor.class);
        OAuth2TokenStateStore sessions = mock(OAuth2TokenStateStore.class);
        OAuth2SessionRevocationStore revocations = mock(OAuth2SessionRevocationStore.class);
        when(access.currentAccessToken()).thenReturn(Optional.of(new CurrentAccessTokenReference(7L, clientId, JTI, EXPIRY)));
        return new Fixture(access, sessions, revocations, new CoreAgentLogoutService(access, sessions, revocations));
    }

    private static OAuth2SessionState session(String clientId, long userId) {
        return new OAuth2SessionState(userId, clientId, JTI, EXPIRY, FINGERPRINT, EXPIRY.plusSeconds(60));
    }

    private record Fixture(CurrentAccessTokenAccessor access, OAuth2TokenStateStore sessions,
                           OAuth2SessionRevocationStore revocations, CoreAgentLogoutService service) {
    }
}
