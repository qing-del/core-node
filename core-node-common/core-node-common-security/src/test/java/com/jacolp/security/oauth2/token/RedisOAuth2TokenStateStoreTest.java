package com.jacolp.security.oauth2.token;

import com.jacolp.common.security.oauth2.token.OAuth2SessionState;
import com.jacolp.common.security.oauth2.token.OAuth2TokenStateCodec;
import com.jacolp.common.security.oauth2.token.RedisOAuth2TokenStateStore;
import com.jacolp.common.security.oauth2.token.RefreshTokenState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lua argument and CAS semantics are unit-tested here; real Redis contention coverage is deferred to Phase 7. */
class RedisOAuth2TokenStateStoreTest {
    private static final String FP = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";
    private static final String OTHER_FP = "BBECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";
    private static final String JTI = "AAECAwQFBgcICQoLDA0ODw";
    private static final String NEXT_JTI = "BAECAwQFBgcICQoLDA0ODw";
    private final OAuth2TokenStateCodec codec = new OAuth2TokenStateCodec();
    private final Instant now = Instant.parse("2026-08-10T00:00:00Z");

    private StringRedisTemplate redis;
    private HashOperations<String, Object, Object> hash;
    private RedisOAuth2TokenStateStore store;
    private RefreshTokenState refresh;
    private OAuth2SessionState session;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void init() {
        redis = mock(StringRedisTemplate.class);
        hash = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hash);
        doReturn(1L).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        refresh = new RefreshTokenState(FP, BCrypt.hashpw("x", BCrypt.gensalt()), 1, "core_agent", List.of("note:read"), now, now.plusSeconds(60));
        session = new OAuth2SessionState(1, "core_agent", JTI, now.plusSeconds(30), FP, now.plusSeconds(60));
        store = new RedisOAuth2TokenStateStore(redis, codec, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    @SuppressWarnings("unchecked")
    void atomicallyReplacesCurrentClientUserSessionWithCodecFieldsAndExactTtl() {
        store.replaceCurrentSession(refresh, session);

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass((Class) RedisScript.class);
        ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(redis).execute(scriptCaptor.capture(), eq(List.of("user:refresh:" + FP, "user:session:core_agent:1")), argumentsCaptor.capture());

        assertThat(argumentsCaptor.getValue()).containsExactly(
                "60000",
                "schema_version", "1",
                "fingerprint", FP,
                "verifier_hash", refresh.verifierHash(),
                "user_id", "1",
                "client_id", "core_agent",
                "granted_scopes", "note:read",
                "issued_at_epoch_millis", Long.toString(now.toEpochMilli()),
                "expires_at_epoch_millis", Long.toString(refresh.expiresAt().toEpochMilli()),
                "60000",
                "schema_version", "1",
                "user_id", "1",
                "client_id", "core_agent",
                "current_access_jti", JTI,
                "access_expires_at_epoch_millis", Long.toString(session.accessExpiresAt().toEpochMilli()),
                "current_refresh_fingerprint", FP,
                "refresh_expires_at_epoch_millis", Long.toString(refresh.expiresAt().toEpochMilli()));
        String script = scriptCaptor.getValue().getScriptAsString();
        assertThat(script)
                .contains("redis.call('DEL', KEYS[1])", "redis.call('DEL', KEYS[2])", "HSET", "PEXPIRE")
                .doesNotContain("access_token", "refresh_token");
    }

    @Test
    void rejectsIncoherentOrExpiredIssuanceStateWithoutCallingRedis() {
        OAuth2SessionState wrongUser = new OAuth2SessionState(2, "core_agent", JTI, now.plusSeconds(30), FP, now.plusSeconds(60));
        OAuth2SessionState wrongClient = new OAuth2SessionState(1, "user_client", JTI, now.plusSeconds(30), FP, now.plusSeconds(60));
        OAuth2SessionState wrongFingerprint = new OAuth2SessionState(1, "core_agent", JTI, now.plusSeconds(30), OTHER_FP, now.plusSeconds(60));
        OAuth2SessionState wrongRefreshExpiry = new OAuth2SessionState(1, "core_agent", JTI, now.plusSeconds(30), FP, now.plusSeconds(59));
        OAuth2SessionState accessAfterRefresh = new OAuth2SessionState(1, "core_agent", JTI, now.plusSeconds(61), FP, now.plusSeconds(60));
        RefreshTokenState expired = new RefreshTokenState(FP, refresh.verifierHash(), 1, "core_agent", List.of(), now.minusSeconds(1), now);

        assertThatIllegalArgumentException().isThrownBy(() -> store.replaceCurrentSession(refresh, wrongUser));
        assertThatIllegalArgumentException().isThrownBy(() -> store.replaceCurrentSession(refresh, wrongClient));
        assertThatIllegalArgumentException().isThrownBy(() -> store.replaceCurrentSession(refresh, wrongFingerprint));
        assertThatIllegalArgumentException().isThrownBy(() -> store.replaceCurrentSession(refresh, wrongRefreshExpiry));
        assertThatIllegalArgumentException().isThrownBy(() -> store.replaceCurrentSession(refresh, accessAfterRefresh));
        assertThatIllegalArgumentException().isThrownBy(() -> store.replaceCurrentSession(expired,
                new OAuth2SessionState(1, "core_agent", JTI, now, FP, now)));
        verify(redis, org.mockito.Mockito.never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void compareAndRotatesOnlyWhenOldRefreshAndSessionPointerStillMatch() {
        RefreshTokenState nextRefresh = nextRefresh();
        OAuth2SessionState nextSession = nextSession(nextRefresh);

        assertThat(store.rotate(FP, nextRefresh, nextSession)).isTrue();

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass((Class) RedisScript.class);
        ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(redis).execute(scriptCaptor.capture(),
                eq(List.of("user:refresh:" + FP, "user:refresh:" + OTHER_FP, "user:session:core_agent:1")),
                argumentsCaptor.capture());
        assertThat(argumentsCaptor.getValue()).containsExactly(
                FP,
                "120000",
                "schema_version", "1",
                "fingerprint", OTHER_FP,
                "verifier_hash", nextRefresh.verifierHash(),
                "user_id", "1",
                "client_id", "core_agent",
                "granted_scopes", "note:read",
                "issued_at_epoch_millis", Long.toString(now.toEpochMilli()),
                "expires_at_epoch_millis", Long.toString(nextRefresh.expiresAt().toEpochMilli()),
                "120000",
                "schema_version", "1",
                "user_id", "1",
                "client_id", "core_agent",
                "current_access_jti", NEXT_JTI,
                "access_expires_at_epoch_millis", Long.toString(nextSession.accessExpiresAt().toEpochMilli()),
                "current_refresh_fingerprint", OTHER_FP,
                "refresh_expires_at_epoch_millis", Long.toString(nextRefresh.expiresAt().toEpochMilli()));
        String script = scriptCaptor.getValue().getScriptAsString();
        int firstMutation = script.indexOf("redis.call('DEL'");
        assertThat(script.indexOf("redis.call('HGET', KEYS[3], 'current_refresh_fingerprint')")).isLessThan(firstMutation);
        assertThat(script.indexOf("redis.call('HGET', KEYS[1], 'fingerprint')")).isLessThan(firstMutation);
        assertThat(script.indexOf("HSET")).isGreaterThan(firstMutation);
        assertThat(script).contains("KEYS[1]", "KEYS[2]", "KEYS[3]", "PEXPIRE").doesNotContain("access_token", "refresh_token");
    }

    @Test
    void returnsFalseWhenCompareAndRotateDoesNotMatch() {
        RefreshTokenState nextRefresh = nextRefresh();
        doReturn(0L).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        assertThat(store.rotate(FP, nextRefresh, nextSession(nextRefresh))).isFalse();
    }

    @Test
    void onlyFirstCompetingCompareAndRotateCanSucceed() {
        RefreshTokenState nextRefresh = nextRefresh();
        OAuth2SessionState nextSession = nextSession(nextRefresh);
        doReturn(1L, 0L).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertThat(store.rotate(FP, nextRefresh, nextSession)).isTrue();
        assertThat(store.rotate(FP, nextRefresh, nextSession)).isFalse();
    }

    @Test
    void failsFastWhenRotationLuaReturnsUnexpectedResult() {
        RefreshTokenState nextRefresh = nextRefresh();
        OAuth2SessionState nextSession = nextSession(nextRefresh);
        doReturn(2L).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        assertThatIllegalStateException().isThrownBy(() -> store.rotate(FP, nextRefresh, nextSession));
        doReturn((Object) null).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        assertThatIllegalStateException().isThrownBy(() -> store.rotate(FP, nextRefresh, nextSession));
    }

    @Test
    void rejectsInvalidNextRotationStateBeforeRunningLua() {
        RefreshTokenState nextRefresh = nextRefresh();
        OAuth2SessionState nextSession = nextSession(nextRefresh);
        OAuth2SessionState wrongFingerprint = new OAuth2SessionState(1, "core_agent", NEXT_JTI, now.plusSeconds(30), FP, nextRefresh.expiresAt());
        OAuth2SessionState lateAccess = new OAuth2SessionState(1, "core_agent", NEXT_JTI, nextRefresh.expiresAt().plusMillis(1), OTHER_FP, nextRefresh.expiresAt());
        RefreshTokenState expired = new RefreshTokenState(OTHER_FP, nextRefresh.verifierHash(), 1, "core_agent", List.of(), now.minusSeconds(1), now);

        assertThatIllegalArgumentException().isThrownBy(() -> store.rotate(FP, refresh, session));
        assertThatIllegalArgumentException().isThrownBy(() -> store.rotate("bad", nextRefresh, nextSession));
        assertThatIllegalArgumentException().isThrownBy(() -> store.rotate(FP, nextRefresh, wrongFingerprint));
        assertThatIllegalArgumentException().isThrownBy(() -> store.rotate(FP, nextRefresh, lateAccess));
        assertThatIllegalArgumentException().isThrownBy(() -> store.rotate(FP, expired,
                new OAuth2SessionState(1, "core_agent", NEXT_JTI, now, OTHER_FP, now)));
        verify(redis, org.mockito.Mockito.never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void failsFastWhenLuaDoesNotAcknowledgeReplacement() {
        doReturn(0L).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        assertThatIllegalStateException().isThrownBy(() -> store.replaceCurrentSession(refresh, session));
    }

    @Test
    void findsStatesAtExactKeysAndDeletes() {
        when(hash.entries("user:refresh:" + FP)).thenReturn(redisHash(codec.encode(refresh)));
        when(hash.entries("user:session:core_agent:1")).thenReturn(redisHash(codec.encode(session)));
        assertThat(store.findRefreshByFingerprint(FP)).contains(refresh);
        assertThat(store.findSession("core_agent", 1)).contains(session);
        store.deleteRefresh(FP);
        store.deleteSession("core_agent", 1);
        verify(redis).delete("user:refresh:" + FP);
        verify(redis).delete("user:session:core_agent:1");
    }

    @Test
    void treatsEmptyHashAsMissingAndRejectsPollution() {
        when(hash.entries(any(String.class))).thenReturn(Map.of());
        assertThat(store.findRefreshByFingerprint(FP)).isEmpty();
        assertThat(store.findSession("core_agent", 1)).isEmpty();
        when(hash.entries("user:refresh:" + FP)).thenReturn(redisHash(Map.of("schema_version", "1")));
        assertThatIllegalArgumentException().isThrownBy(() -> store.findRefreshByFingerprint(FP));
    }

    @Test
    void rejectsKeyStateMismatch() {
        Map<String, String> polluted = new HashMap<>(codec.encode(refresh));
        polluted.put("fingerprint", OTHER_FP);
        when(hash.entries("user:refresh:" + FP)).thenReturn(redisHash(polluted));
        assertThatIllegalArgumentException().isThrownBy(() -> store.findRefreshByFingerprint(FP));
        Map<String, String> badSession = new HashMap<>(codec.encode(session));
        badSession.put("client_id", "user_client");
        when(hash.entries("user:session:core_agent:1")).thenReturn(redisHash(badSession));
        assertThatIllegalArgumentException().isThrownBy(() -> store.findSession("core_agent", 1));
    }

    @Test
    void rejectsNonStringHashEntriesAndInvalidInputs() {
        when(hash.entries("user:refresh:" + FP)).thenReturn(Map.of(1, "x"));
        assertThatIllegalArgumentException().isThrownBy(() -> store.findRefreshByFingerprint(FP));
        assertThatIllegalArgumentException().isThrownBy(() -> store.deleteRefresh("bad"));
        assertThatIllegalArgumentException().isThrownBy(() -> store.deleteSession("bad:client", 1));
    }

    private static Map<Object, Object> redisHash(Map<String, String> values) {
        Map<Object, Object> result = new LinkedHashMap<>();
        result.putAll(values);
        return result;
    }

    private RefreshTokenState nextRefresh() {
        return new RefreshTokenState(OTHER_FP, BCrypt.hashpw("next", BCrypt.gensalt()), 1, "core_agent", List.of("note:read"), now, now.plusSeconds(120));
    }

    private OAuth2SessionState nextSession(RefreshTokenState nextRefresh) {
        return new OAuth2SessionState(1, "core_agent", NEXT_JTI, now.plusSeconds(30), nextRefresh.fingerprint(), nextRefresh.expiresAt());
    }
}
