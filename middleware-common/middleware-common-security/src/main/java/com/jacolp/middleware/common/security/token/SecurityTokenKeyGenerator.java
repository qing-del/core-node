package com.jacolp.middleware.common.security.token;

import org.jspecify.annotations.NonNull;

public final class SecurityTokenKeyGenerator {

    private SecurityTokenKeyGenerator() {
    }
    /**
     * 获取用户登录令牌对应的 key
     * @param userId
     * @return
     */
    public static @NonNull String getAdminLoginKey(Long userId) {
        return SecurityTokenConstants.ADMIN_ID_CLAIM + ":" + userId;
    }

    /**
     * 获取用户登录标识 key
     * @param userId 用户ID
     * @return 登录标识
     */
    public static @NonNull String getUserLoginKey(Long userId) {
        return SecurityTokenConstants.USER_ID_CLAIM + ":" + userId;
    }

    /**
     * 获取激活码对应的 Redis key（6位数字 → userId）
     * @param code 6位激活码
     * @return Redis key
     */
    public static @NonNull String getActiveCodeKey(String code) {
        return SecurityTokenConstants.ACTIVE_CODE_PREFIX + code;
    }

    /**
     * 获取激活邮件发送冷却 key
     * @param userId 用户ID
     * @return 冷却 key
     */
    public static @NonNull String getActivationEmailCooldownKey(Long userId) {
        return SecurityTokenConstants.ACTIVE_EMAIL_SEND_COOLDOWN_PREFIX + userId;
    }

    /**
     * 获取邮箱更改验证码对应的 Redis key（6位数字 → userId|newEmail）
     * @param code 6位验证码
     * @return Redis key
     */
    public static @NonNull String getEmailChangeCodeKey(String code) {
        return SecurityTokenConstants.EMAIL_CHANGE_CODE_PREFIX + code;
    }
}
