package com.jacolp.security.activation;

import com.jacolp.common.security.activation.AccountVerificationCredentialService;
import com.jacolp.common.security.activation.ActivationJwtTokenSupport;
import com.jacolp.common.security.activation.RedisAccountVerificationCredentialService;
import com.jacolp.common.security.jwt.JwtProperties;
import com.jacolp.common.security.token.SecurityTokenConstants;
import com.jacolp.common.security.token.SecurityTokenKeyGenerator;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RedisAccountVerificationCredentialServiceTest {
    private static final String ACTIVE_SECRET = "active-secret-key-for-token-session-tests";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisAccountVerificationCredentialService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        JwtProperties properties = new JwtProperties();
        properties.setActiveSecretKey(ACTIVE_SECRET);
        properties.setActiveTtl(60003L);
        properties.setActiveCodeTtl(60004L);
        service = new RedisAccountVerificationCredentialService(redis, properties);
    }

    @Test
    void issuesActivationTokenWithTheExistingClaimsAndTtl() {
        long started = System.currentTimeMillis();
        Claims claims = ActivationJwtTokenSupport.parseActivationJwt(ACTIVE_SECRET, service.issueActivationToken(13L));
        long finished = System.currentTimeMillis();

        assertThat(claims.get(SecurityTokenConstants.USER_ID_CLAIM).toString()).isEqualTo("13");
        assertThat(claims.get(SecurityTokenConstants.ACTIVE_SIGN_KEY)).isEqualTo(true);
        assertThat(claims.getExpiration().getTime()).isBetween(started + 59003L, finished + 60003L);
    }

    @Test
    void storesCodesCooldownAndEmailChangePayloadsWithExistingTtls() {
        service.saveActivationCode("123456", 5L);
        service.saveEmailChangeCode("654321", 6L, "new@test.com");
        verify(values).set(eq(SecurityTokenKeyGenerator.getActiveCodeKey("123456")), eq("5"),
                eq(java.time.Duration.ofMillis(60004L)));
        verify(values).set(eq(SecurityTokenKeyGenerator.getEmailChangeCodeKey("654321")), eq("6|new@test.com"),
                eq(java.time.Duration.ofMillis(60003L)));
        when(values.setIfAbsent(eq(SecurityTokenKeyGenerator.getActivationEmailCooldownKey(5L)), eq("1"),
                eq(java.time.Duration.ofSeconds(60)))).thenReturn(true, false);
        assertThat(service.acquireActivationEmailCooldown(5L)).isTrue();
        assertThat(service.acquireActivationEmailCooldown(5L)).isFalse();
        when(values.get(SecurityTokenKeyGenerator.getActiveCodeKey("123456"))).thenReturn("5");
        assertThat(service.findActivationCodeUserId("123456")).isEqualTo(5L);
        service.deleteActivationCode("123456");
        verify(redis).delete(SecurityTokenKeyGenerator.getActiveCodeKey("123456"));
        assertThat(service.activationLinkExpiryMinutes()).isEqualTo(1L);
        assertThat(service.activationCodeExpiryMinutes()).isEqualTo(1L);
        assertThat(service.emailChangeCodeExpiryMinutes()).isEqualTo(1L);
    }

    @Test
    void parsesAndDeletesEmailChangeCodesWithLegacyFailureRules() {
        String key = SecurityTokenKeyGenerator.getEmailChangeCodeKey("code");
        when(values.get(key)).thenReturn("7|mail@test.com", null, "broken", "bad|mail@test.com");
        assertThat(service.findEmailChangeCode("code"))
                .isEqualTo(new AccountVerificationCredentialService.EmailChangeCode(7L, "mail@test.com"));
        assertThat(service.findEmailChangeCode("code")).isNull();
        assertThat(service.findEmailChangeCode("code")).isNull();
        verify(redis).delete(key);
        assertThatThrownBy(() -> service.findEmailChangeCode("code")).isInstanceOf(NumberFormatException.class);
        verify(redis, times(1)).delete(key);
        service.deleteEmailChangeCode("code");
        verify(redis, times(2)).delete(key);
    }
}
