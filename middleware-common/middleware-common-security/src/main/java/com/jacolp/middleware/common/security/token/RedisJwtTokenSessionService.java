package com.jacolp.middleware.common.security.token;

import com.jacolp.middleware.common.security.jwt.JwtProperties;
import com.jacolp.middleware.common.security.jwt.JwtTokenSupport;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class RedisJwtTokenSessionService implements TokenSessionService {
    private final StringRedisTemplate redis;
    private final JwtProperties properties;

    public RedisJwtTokenSessionService(StringRedisTemplate redis, JwtProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public String issueUserLoginToken(Long userId) { return issueLogin(userId, false); }
    public String issueAdminLoginToken(Long adminId) { return issueLogin(adminId, true); }
    public void revokeUserLoginToken(Long userId) { redis.delete(SecurityTokenKeyGenerator.getUserLoginKey(userId)); }
    public void revokeAdminLoginToken(Long adminId) { redis.delete(SecurityTokenKeyGenerator.getAdminLoginKey(adminId)); }
    public String issueActivationToken(Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(SecurityTokenConstants.ACTIVE_SIGN_KEY, true);
        claims.put(SecurityTokenConstants.USER_ID_CLAIM, userId);
        return JwtTokenSupport.createJWT(properties.getActiveSecretKey(), properties.getActiveTtl(), claims);
    }
    public void saveActivationCode(String code, Long userId) {
        redis.opsForValue().set(SecurityTokenKeyGenerator.getActiveCodeKey(code), String.valueOf(userId),
                Duration.ofMillis(properties.getActiveCodeTtl()));
    }
    public Long findActivationCodeUserId(String code) {
        String value = redis.opsForValue().get(SecurityTokenKeyGenerator.getActiveCodeKey(code));
        return value == null ? null : Long.valueOf(value);
    }
    public void deleteActivationCode(String code) { redis.delete(SecurityTokenKeyGenerator.getActiveCodeKey(code)); }
    public boolean acquireActivationEmailCooldown(Long userId) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                SecurityTokenKeyGenerator.getActivationEmailCooldownKey(userId), "1", Duration.ofSeconds(60)));
    }
    public void saveEmailChangeCode(String code, Long userId, String newEmail) {
        redis.opsForValue().set(SecurityTokenKeyGenerator.getEmailChangeCodeKey(code), userId + "|" + newEmail,
                Duration.ofMillis(properties.getActiveTtl()));
    }
    public EmailChangeCode findEmailChangeCode(String code) {
        String value = redis.opsForValue().get(SecurityTokenKeyGenerator.getEmailChangeCodeKey(code));
        if (value == null) return null;
        int pipe = value.lastIndexOf('|');
        if (pipe <= 0 || pipe >= value.length() - 1) {
            redis.delete(SecurityTokenKeyGenerator.getEmailChangeCodeKey(code));
            return null;
        }
        return new EmailChangeCode(Long.valueOf(value.substring(0, pipe)), value.substring(pipe + 1));
    }
    public void deleteEmailChangeCode(String code) { redis.delete(SecurityTokenKeyGenerator.getEmailChangeCodeKey(code)); }
    public long activationLinkExpiryMinutes() { return properties.getActiveTtl() / 60000; }
    public long activationCodeExpiryMinutes() { return properties.getActiveCodeTtl() / 60000; }
    public long emailChangeCodeExpiryMinutes() { return properties.getActiveTtl() / 60000; }
    private String issueLogin(Long id, boolean admin) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(admin ? SecurityTokenConstants.ADMIN_ID_CLAIM : SecurityTokenConstants.USER_ID_CLAIM, id);
        String token = JwtTokenSupport.createJWT(admin ? properties.getAdminSecretKey() : properties.getUserSecretKey(),
                admin ? properties.getAdminTtl() : properties.getUserTtl(), claims);
        redis.opsForValue().set(admin ? SecurityTokenKeyGenerator.getAdminLoginKey(id)
                : SecurityTokenKeyGenerator.getUserLoginKey(id), token);
        return token;
    }
}
