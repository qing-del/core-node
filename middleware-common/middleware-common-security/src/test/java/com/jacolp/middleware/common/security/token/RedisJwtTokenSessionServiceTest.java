package com.jacolp.middleware.common.security.token;

import com.jacolp.middleware.common.security.jwt.JwtProperties;
import com.jacolp.middleware.common.security.jwt.JwtTokenSupport;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RedisJwtTokenSessionServiceTest {
    private static final String USER_SECRET = "user-secret-key-for-token-session-tests";
    private static final String ADMIN_SECRET = "admin-secret-key-for-token-session-tests";
    private static final String ACTIVE_SECRET = "active-secret-key-for-token-session-tests";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisJwtTokenSessionService service;
    private JwtProperties properties;

    @BeforeEach @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class); values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        properties = new JwtProperties();
        properties.setUserSecretKey(USER_SECRET); properties.setAdminSecretKey(ADMIN_SECRET); properties.setActiveSecretKey(ACTIVE_SECRET);
        properties.setUserTtl(60001L); properties.setAdminTtl(60002L); properties.setActiveTtl(60003L); properties.setActiveCodeTtl(60004L);
        service = new RedisJwtTokenSessionService(redis, properties);
    }

    @Test void issuesUserAndAdminTokensWithoutRedisTtl() {
        long userStarted = System.currentTimeMillis();
        String user = service.issueUserLoginToken(11L);
        long userFinished = System.currentTimeMillis();
        long adminStarted = System.currentTimeMillis();
        String admin = service.issueAdminLoginToken(12L);
        long adminFinished = System.currentTimeMillis();
        Claims uc = JwtTokenSupport.parseJWT(USER_SECRET, user); Claims ac = JwtTokenSupport.parseJWT(ADMIN_SECRET, admin);
        assertThat(uc.get(SecurityTokenConstants.USER_ID_CLAIM).toString()).isEqualTo("11");
        assertThat(ac.get(SecurityTokenConstants.ADMIN_ID_CLAIM).toString()).isEqualTo("12");
        assertThat(uc.getExpiration().getTime()).isBetween(userStarted + 59001L, userFinished + 60001L);
        assertThat(ac.getExpiration().getTime()).isBetween(adminStarted + 59002L, adminFinished + 60002L);
        verify(values).set(SecurityTokenKeyGenerator.getUserLoginKey(11L), user);
        verify(values).set(SecurityTokenKeyGenerator.getAdminLoginKey(12L), admin);
        verify(values, never()).set(any(), any(), any(java.time.Duration.class));
    }

    @Test void revokesSessionsAndIssuesActivationToken() {
        service.revokeUserLoginToken(11L); service.revokeAdminLoginToken(12L);
        verify(redis).delete(SecurityTokenKeyGenerator.getUserLoginKey(11L)); verify(redis).delete(SecurityTokenKeyGenerator.getAdminLoginKey(12L));
        long started = System.currentTimeMillis();
        Claims claims = JwtTokenSupport.parseJWT(ACTIVE_SECRET, service.issueActivationToken(13L));
        long finished = System.currentTimeMillis();
        assertThat(claims.get(SecurityTokenConstants.USER_ID_CLAIM).toString()).isEqualTo("13");
        assertThat(claims.get(SecurityTokenConstants.ACTIVE_SIGN_KEY)).isEqualTo(true);
        assertThat(claims.getExpiration().getTime()).isBetween(started + 59003L, finished + 60003L);
    }

    @Test void storesCodesCooldownAndEmailChangePayloadsWithExistingTtls() {
        service.saveActivationCode("123456", 5L); service.saveEmailChangeCode("654321", 6L, "new@test.com");
        verify(values).set(eq(SecurityTokenKeyGenerator.getActiveCodeKey("123456")), eq("5"), eq(java.time.Duration.ofMillis(60004L)));
        verify(values).set(eq(SecurityTokenKeyGenerator.getEmailChangeCodeKey("654321")), eq("6|new@test.com"), eq(java.time.Duration.ofMillis(60003L)));
        when(values.setIfAbsent(eq(SecurityTokenKeyGenerator.getActivationEmailCooldownKey(5L)), eq("1"), eq(java.time.Duration.ofSeconds(60)))).thenReturn(true, false);
        assertThat(service.acquireActivationEmailCooldown(5L)).isTrue(); assertThat(service.acquireActivationEmailCooldown(5L)).isFalse();
        when(values.get(SecurityTokenKeyGenerator.getActiveCodeKey("123456"))).thenReturn("5");
        assertThat(service.findActivationCodeUserId("123456")).isEqualTo(5L);
        service.deleteActivationCode("123456");
        verify(redis).delete(SecurityTokenKeyGenerator.getActiveCodeKey("123456"));
        assertThat(service.activationLinkExpiryMinutes()).isEqualTo(1L); assertThat(service.activationCodeExpiryMinutes()).isEqualTo(1L); assertThat(service.emailChangeCodeExpiryMinutes()).isEqualTo(1L);
    }

    @Test void parsesAndDeletesEmailChangeCodesWithLegacyFailureRules() {
        String key = SecurityTokenKeyGenerator.getEmailChangeCodeKey("code");
        when(values.get(key)).thenReturn("7|mail@test.com", null, "broken", "bad|mail@test.com");
        assertThat(service.findEmailChangeCode("code")).isEqualTo(new TokenSessionService.EmailChangeCode(7L, "mail@test.com"));
        assertThat(service.findEmailChangeCode("code")).isNull();
        assertThat(service.findEmailChangeCode("code")).isNull(); verify(redis).delete(key);
        assertThatThrownBy(() -> service.findEmailChangeCode("code")).isInstanceOf(NumberFormatException.class);
        verify(redis, times(1)).delete(key);
        service.deleteEmailChangeCode("code"); verify(redis, times(2)).delete(key);
    }
}
