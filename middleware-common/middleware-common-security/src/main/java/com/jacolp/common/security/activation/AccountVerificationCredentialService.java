package com.jacolp.common.security.activation;

/**
 * Credential boundary for account activation and verified email-change workflows.
 *
 * <p>These credentials are intentionally separate from user and administrator bearer sessions.
 * The activation-link JWT remains a legacy, narrow-purpose credential until its dedicated
 * replacement is planned.</p>
 */
public interface AccountVerificationCredentialService {
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
