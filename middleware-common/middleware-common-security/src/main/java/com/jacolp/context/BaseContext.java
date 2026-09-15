package com.jacolp.context;

import com.jacolp.middleware.common.security.context.AuthenticationContext;
import com.jacolp.middleware.common.security.context.SecurityContextBridge;

/**
 * 旧业务代码使用的认证上下文兼容入口。
 */
public final class BaseContext {

    private BaseContext() {
    }

    public static void setCurrentId(Long id) {
        AuthenticationContext.setCurrentId(id);
    }

    public static Long getCurrentId() {
        Long id = SecurityContextBridge.currentIdOrNull();
        return id != null ? id : AuthenticationContext.getCurrentId();
    }

    public static Long getCurrentIdWithoutValid() {
        Long id = SecurityContextBridge.currentIdOrNull();
        return id != null ? id : AuthenticationContext.getCurrentIdWithoutValidation();
    }

    public static void remove() {
        AuthenticationContext.clear();
    }
}
