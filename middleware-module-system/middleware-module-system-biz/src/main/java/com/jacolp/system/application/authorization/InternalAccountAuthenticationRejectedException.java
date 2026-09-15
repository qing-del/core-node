package com.jacolp.system.application.authorization;

import com.jacolp.common.core.exception.AuthenticationException;

/**
 * Identifiable rejection reasons for ordinary USER/ADMIN authentication and email-code issuance failures.
 */
public final class InternalAccountAuthenticationRejectedException extends AuthenticationException {

    public enum Reason {
        GENERIC("登录认证失败，请检查登录信息", "Internal account authentication rejected"),
        ACCOUNT_NOT_FOUND("邮箱未注册，无法发送验证码", "Internal email-code account was not found"),
        EMAIL_MISMATCH("请求邮箱与账号邮箱不一致，无法发送验证码",
                "Internal email-code account email does not match request"),
        ACCOUNT_NOT_ACTIVATED("账号未激活，请先激活账号", "Internal login account is not activated"),
        ACCOUNT_DISABLED("账号已被禁用，请联系管理员", "Internal login account is disabled"),
        GRANT_TYPE_NOT_ALLOWED("当前账号不支持该登录方式", "Internal login grant type is not enabled for account"),
        ROLE_NOT_ALLOWED("当前账号角色不允许使用该登录客户端", "Internal login role is not allowed for client"),
        NO_EFFECTIVE_PERMISSION("当前账号没有可用的访问权限", "Internal login has no effective permission"),
        IP_NOT_ALLOWED("当前 IP 不在允许的登录范围内", "Internal login IP is not allowed"),
        UNSUPPORTED_CLIENT("不支持当前登录客户端", "Internal login client is unsupported"),
        UNSUPPORTED_GRANT_TYPE("不支持当前登录方式", "Internal login grant type is unsupported"),
        EMAIL_CODE_INVALID("邮箱验证码错误或已过期", "Internal login email code is invalid or expired");

        private final String message;
        private final String logMessage;

        Reason(String message, String logMessage) {
            this.message = message;
            this.logMessage = logMessage;
        }

        public String message() {
            return message;
        }

        public String logMessage() {
            return logMessage;
        }
    }

    public static final String MESSAGE = Reason.GENERIC.message();

    private final Reason reason;

    public InternalAccountAuthenticationRejectedException() {
        this(Reason.GENERIC);
    }

    public InternalAccountAuthenticationRejectedException(Reason reason) {
        super(requireReason(reason).message(), reason.logMessage());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    private static Reason requireReason(Reason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("Internal authentication rejection reason is required");
        }
        return reason;
    }
}
