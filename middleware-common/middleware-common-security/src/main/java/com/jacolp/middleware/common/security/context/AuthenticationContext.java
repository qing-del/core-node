package com.jacolp.middleware.common.security.context;

import com.jacolp.exception.AuthenticationException;

public final class AuthenticationContext {

    private static final ThreadLocal<Long> CURRENT_ID = new ThreadLocal<>();

    private AuthenticationContext() {
    }

    public static void setCurrentId(Long id) {
        CURRENT_ID.set(id);
    }

    public static Long getCurrentId() {
        Long currentId = CURRENT_ID.get();
        if (currentId == null) {
            throw new AuthenticationException("当前登录信息已失效");
        }
        return currentId;
    }

    public static Long getCurrentIdWithoutValidation() {
        return CURRENT_ID.get();
    }

    public static void clear() {
        CURRENT_ID.remove();
    }
}
