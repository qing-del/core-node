package com.jacolp.security.oauth2.token;

import com.jacolp.common.security.oauth2.token.OAuth2SessionRevocationRequest;
import com.jacolp.common.security.oauth2.token.RedisOAuth2SessionRevocationStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RedisOAuth2SessionRevocationStoreTest {
    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");
    private static final String JTI = "0123456789abcdefghijkl";
    private static final String FINGERPRINT = "0123456789abcdefghijklmnopqrstuvwxyzaBcDeFg";

    @Test
    @SuppressWarnings("unchecked")
    void revokesWithoutRefreshCasUsingExactKeysAndTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doReturn(1L).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        RedisOAuth2SessionRevocationStore store = store(redis);

        assertThat(store.revoke(request(null))).isTrue();

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(redis).execute(any(RedisScript.class), keys.capture(), args.capture());
        assertThat(keys.getValue()).containsExactly("user:blacklist:access:" + JTI, "user:session:user:7");
        assertThat(args.getValue()).containsExactly("60000", "");
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesRefreshCasAndMapsStaleNullAndUnexpectedResultsFailClosed() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doReturn(0L, null, 2L).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        RedisOAuth2SessionRevocationStore store = store(redis);

        assertThat(store.revoke(request(FINGERPRINT))).isFalse();
        assertThatThrownBy(() -> store.revoke(request(FINGERPRINT))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.revoke(request(FINGERPRINT))).isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(redis, org.mockito.Mockito.times(3)).execute(any(RedisScript.class), keys.capture(), args.capture());
        assertThat(keys.getAllValues().get(0)).containsExactly("user:blacklist:access:" + JTI,
                "user:session:user:7", "user:refresh:" + FINGERPRINT);
        assertThat(args.getAllValues().get(0)).containsExactly("60000", FINGERPRINT);
    }

    @Test
    void rejectsExpiredRequestAndLuaValidatesBeforeWrites() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisOAuth2SessionRevocationStore store = store(redis);
        OAuth2SessionRevocationRequest expired = new OAuth2SessionRevocationRequest(7, "user", JTI, NOW, null);
        assertThatIllegalArgumentException().isThrownBy(() -> store.revoke(expired));

        String lua = Files.readString(Path.of("src/main/resources/oauth2/token/revoke-current-session.lua"));
        assertThat(lua.indexOf("if (#KEYS")).isLessThan(lua.indexOf("redis.call('SET'"));
        assertThat(lua).contains("current_refresh_fingerprint", "redis.call('EXISTS', KEYS[2])", "#expected ~= 43",
                "'PX'", "if expected ~= '' then redis.call('DEL', KEYS[2]) end")
                .doesNotContain("tokenValue", "raw_token");
    }

    @Test
    void requestIsStrictAndRedacted() {
        assertThat(request(FINGERPRINT).toString()).doesNotContain(JTI, FINGERPRINT);
        assertThatIllegalArgumentException().isThrownBy(() -> new OAuth2SessionRevocationRequest(0, "user", JTI, NOW.plusSeconds(1), null));
        assertThatIllegalArgumentException().isThrownBy(() -> new OAuth2SessionRevocationRequest(7, "bad client", JTI, NOW.plusSeconds(1), null));
        assertThatIllegalArgumentException().isThrownBy(() -> new OAuth2SessionRevocationRequest(7, "user", "bad", NOW.plusSeconds(1), null));
    }

    private static RedisOAuth2SessionRevocationStore store(StringRedisTemplate redis) {
        return new RedisOAuth2SessionRevocationStore(redis, Clock.fixed(NOW, ZoneOffset.UTC), script());
    }

    private static DefaultRedisScript<Long> script() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        return script;
    }

    private static OAuth2SessionRevocationRequest request(String fingerprint) {
        return new OAuth2SessionRevocationRequest(7, "user", JTI, NOW.plusSeconds(60), fingerprint);
    }
}
