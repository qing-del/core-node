package com.jacolp.middleware.common.security.token;

/** Stable token and Redis-session boundary for account workflows. */
public interface TokenSessionService {
    String issueUserLoginToken(Long userId);
    String issueAdminLoginToken(Long adminId);
    void revokeUserLoginToken(Long userId);
    void revokeAdminLoginToken(Long adminId);
    String issueActivationToken(Long userId);
    void saveActivationCode(String code, Long userId);
    Long findActivationCodeUserId(String code);
    void deleteActivationCode(String code);
    boolean acquireActivationEmailCooldown(Long userId);
    void saveEmailChangeCode(String code, Long userId, String newEmail);
    EmailChangeCode findEmailChangeCode(String code);
    void deleteEmailChangeCode(String code);
    long activationLinkExpiryMinutes();
    long activationCodeExpiryMinutes();
    long emailChangeCodeExpiryMinutes();

    record EmailChangeCode(Long userId, String newEmail) { }
}
