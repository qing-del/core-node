package com.jacolp.system.infrastructure.authorization;

import com.jacolp.system.application.authorization.EmailLoginCodeStateCodec;
import com.jacolp.system.application.authorization.model.EmailLoginCodeState;
import com.jacolp.system.application.port.out.EmailLoginCodeStateStore;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisEmailLoginCodeStateStoreTest {

    private static final String USER_KEY = "user:email_code:user:7";

    @Test
    void portDoesNotExposeInfrastructureTypesAndProductionConstructorIsAutowired() throws Exception {
        for (Method method : EmailLoginCodeStateStore.class.getDeclaredMethods()) {
            assertThat(method.toGenericString()).doesNotContain(".infrastructure.", "dataobject");
        }
        assertThat(RedisEmailLoginCodeStateStore.class.getConstructor(StringRedisTemplate.class)
                .isAnnotationPresent(Autowired.class)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findsExactKeyAndDecodesMatchingState() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hash);
        EmailLoginCodeState state = state("user", 7L);
        when(hash.entries(USER_KEY)).thenReturn(new LinkedHashMap<>(new EmailLoginCodeStateCodec().encode(state)));

        Assertions.assertThat(new RedisEmailLoginCodeStateStore(redis).find("user", 7L)).contains(state);
        verify(hash).entries(USER_KEY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nullAndEmptyHashesAreMissing() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hash);
        when(hash.entries(USER_KEY)).thenReturn(null, Map.of());
        RedisEmailLoginCodeStateStore store = new RedisEmailLoginCodeStateStore(redis);

        Assertions.assertThat(store.find("user", 7L)).isEmpty();
        Assertions.assertThat(store.find("user", 7L)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsNonStringHashFieldsAndValues() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hash);
        when(hash.entries(USER_KEY)).thenReturn(Map.of(1, "x"), Map.of("field", 1));
        RedisEmailLoginCodeStateStore store = new RedisEmailLoginCodeStateStore(redis);

        assertThatIllegalArgumentException().isThrownBy(() -> store.find("user", 7L));
        assertThatIllegalArgumentException().isThrownBy(() -> store.find("user", 7L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsBadSchemasAndKeyStateMismatches() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hash);
        Map<String, String> badSchema = new HashMap<>(new EmailLoginCodeStateCodec().encode(state("user", 7L)));
        badSchema.put("schema_version", "2");
        Map<String, String> mismatchedState = new EmailLoginCodeStateCodec().encode(state("admin", 7L));
        when(hash.entries(USER_KEY)).thenReturn(new LinkedHashMap<>(badSchema), new LinkedHashMap<>(mismatchedState));
        RedisEmailLoginCodeStateStore store = new RedisEmailLoginCodeStateStore(redis);

        assertThatIllegalArgumentException().isThrownBy(() -> store.find("user", 7L));
        assertThatIllegalArgumentException().isThrownBy(() -> store.find("user", 7L));
    }

    @Test
    void deleteUsesExactKeyAndInvalidParametersFailFast() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisEmailLoginCodeStateStore store = new RedisEmailLoginCodeStateStore(redis);

        store.delete("admin", 9L);
        verify(redis).delete("user:email_code:admin:9");
        assertThatIllegalArgumentException().isThrownBy(() -> store.find("core_agent", 1L));
        assertThatIllegalArgumentException().isThrownBy(() -> store.find("user", null));
        assertThatIllegalArgumentException().isThrownBy(() -> store.delete("user", 0L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisExceptionsPropagate() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hash);
        IllegalStateException failure = new IllegalStateException("redis unavailable");
        when(hash.entries(USER_KEY)).thenThrow(failure);

        assertThatThrownBy(() -> new RedisEmailLoginCodeStateStore(redis).find("user", 7L)).isSameAs(failure);
    }

    private static EmailLoginCodeState state(String client, Long id) {
        return new EmailLoginCodeState(client, id, "A".repeat(43), "$2a$10$" + "a".repeat(53), 0,
                Instant.ofEpochMilli(1), Instant.ofEpochMilli(2));
    }
}
