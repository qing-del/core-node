package com.jacolp.common.security.token;

public final class SecurityTokenConstants {

    public static final String USER_ID_CLAIM = "userId";
    public static final String ACTIVE_SIGN_KEY = "active";
    public static final String ACTIVE_CODE_PREFIX = "active:code:";
    public static final String ACTIVE_EMAIL_SEND_COOLDOWN_PREFIX = "active:send-cooldown:";
    public static final String EMAIL_CHANGE_CODE_PREFIX = "emailchange:code:";

    private SecurityTokenConstants() {
    }
}
