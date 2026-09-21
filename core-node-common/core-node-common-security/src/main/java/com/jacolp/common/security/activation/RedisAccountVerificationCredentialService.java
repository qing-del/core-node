package com.jacolp.common.security.activation;

import com.jacolp.common.security.jwt.JwtProperties;
import com.jacolp.common.security.token.SecurityTokenKeyGenerator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** Redis adapter that preserves the existing activation and email-change credential contract. */
@Service
public class RedisAccountVerificationCredentialService implements AccountVerificationCredentialService {
    private final StringRedisTemplate redis;
    private final JwtProperties properties;

    public RedisAccountVerificationCredentialService(StringRedisTemplate redis, JwtProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public String issueActivationToken(Long userId) {
        return ActivationJwtTokenSupport.issueActivationToken(properties.getActiveSecretKey(), properties.getActiveTtl(), userId);
    }

    @Override
    public void saveActivationCode(String code, Long userId) {
        redis.opsForValue().set(SecurityTokenKeyGenerator.getActiveCodeKey(code), String.valueOf(userId),
                Duration.ofMillis(properties.getActiveCodeTtl()));
    }

    @Override
    public Long findActivationCodeUserId(String code) {
        String value = redis.opsForValue().get(SecurityTokenKeyGenerator.getActiveCodeKey(code));
        return value == null ? null : Long.valueOf(value);
    }

    @Override
    public void deleteActivationCode(String code) {
        redis.delete(SecurityTokenKeyGenerator.getActiveCodeKey(code));
    }

    @Override
    public boolean acquireActivationEmailCooldown(Long userId) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                SecurityTokenKeyGenerator.getActivationEmailCooldownKey(userId), "1", Duration.ofSeconds(60)));
    }

    @Override
    public void saveEmailChangeCode(String code, Long userId, String newEmail) {
        redis.opsForValue().set(SecurityTokenKeyGenerator.getEmailChangeCodeKey(code), userId + "|" + newEmail,
                Duration.ofMillis(properties.getActiveTtl()));
    }

    @Override
    public EmailChangeCode findEmailChangeCode(String code) {
        String value = redis.opsForValue().get(SecurityTokenKeyGenerator.getEmailChangeCodeKey(code));
        if (value == null) {
            return null;
        }
        int pipe = value.lastIndexOf('|');
        if (pipe <= 0 || pipe >= value.length() - 1) {
            redis.delete(SecurityTokenKeyGenerator.getEmailChangeCodeKey(code));
            return null;
        }
        return new EmailChangeCode(Long.valueOf(value.substring(0, pipe)), value.substring(pipe + 1));
    }

    @Override
    public void deleteEmailChangeCode(String code) {
        redis.delete(SecurityTokenKeyGenerator.getEmailChangeCodeKey(code));
    }

    @Override
    public long activationLinkExpiryMinutes() {
        return properties.getActiveTtl() / 60000;
    }

    @Override
    public long activationCodeExpiryMinutes() {
        return properties.getActiveCodeTtl() / 60000;
    }

    @Override
    public long emailChangeCodeExpiryMinutes() {
        return properties.getActiveTtl() / 60000;
    }
}
