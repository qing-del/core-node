package com.jacolp.common.security.token;

import org.jspecify.annotations.NonNull;

public final class SecurityTokenKeyGenerator {

    private SecurityTokenKeyGenerator() {
    }

    public static @NonNull String getActiveCodeKey(String code) {
        return SecurityTokenConstants.ACTIVE_CODE_PREFIX + code;
    }

    public static @NonNull String getActivationEmailCooldownKey(Long userId) {
        return SecurityTokenConstants.ACTIVE_EMAIL_SEND_COOLDOWN_PREFIX + userId;
    }

    public static @NonNull String getEmailChangeCodeKey(String code) {
        return SecurityTokenConstants.EMAIL_CHANGE_CODE_PREFIX + code;
    }
}
