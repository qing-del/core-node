package com.jacolp.system.application.authorization;

import com.jacolp.common.core.exception.AuthenticationException;

/** Uniform non-enumerating rejection for a USER/ADMIN refresh-token grant. */
public final class InternalRefreshTokenRejectedException extends AuthenticationException {

    public static final String MESSAGE = "登录状态已失效，请重新登录";
    public static final String LOG_MESSAGE = "Internal refresh token is invalid or expired";

    public InternalRefreshTokenRejectedException() {
        super(MESSAGE, LOG_MESSAGE);
    }
}
