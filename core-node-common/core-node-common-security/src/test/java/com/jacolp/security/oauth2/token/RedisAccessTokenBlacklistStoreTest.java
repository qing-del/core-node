package com.jacolp.security.oauth2.token;

import com.jacolp.common.security.oauth2.token.RedisAccessTokenBlacklistStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.*;

class RedisAccessTokenBlacklistStoreTest {
    private static final String JTI = "AAECAwQFBgcICQoLDA0ODw";
    private final Instant now = Instant.parse("2026-08-10T00:00:00Z");
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisAccessTokenBlacklistStore store;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class); values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        store = new RedisAccessTokenBlacklistStore(redis, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test void writesOnlyMarkerAtExpectedKeyAndTtl() {
        store.blacklist(JTI, now.plusSeconds(90));
        verify(values).set("user:blacklist:access:" + JTI, "1", java.time.Duration.ofSeconds(90));
        verifyNoMoreInteractions(values);
    }

    @Test void checksKeyExistenceOnly() {
        when(redis.hasKey("user:blacklist:access:" + JTI)).thenReturn(true);
        assertThat(store.isBlacklisted(JTI)).isTrue();
        when(redis.hasKey("user:blacklist:access:" + JTI)).thenReturn(false);
        assertThat(store.isBlacklisted(JTI)).isFalse();
    }

    @Test void doesNotWriteExpiredTokenAndRejectsInvalidInput() {
        store.blacklist(JTI, now); store.blacklist(JTI, now.minusSeconds(1));
        verifyNoInteractions(values);
        assertThatIllegalArgumentException().isThrownBy(() -> store.isBlacklisted(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> store.isBlacklisted("short"));
        assertThatIllegalArgumentException().isThrownBy(() -> store.isBlacklisted("AAECAwQFBgcICQoLDA0OD+"));
        assertThatIllegalArgumentException().isThrownBy(() -> store.blacklist(null, now.plusSeconds(1)));
    }
}
