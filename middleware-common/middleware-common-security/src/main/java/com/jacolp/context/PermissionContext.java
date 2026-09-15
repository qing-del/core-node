package com.jacolp.context;

import com.jacolp.middleware.common.security.context.AuthorizationContext;
import com.jacolp.middleware.common.security.context.SecurityContextBridge;

/**
 * 旧业务代码使用的权限上下文兼容入口。
 */
public final class PermissionContext {

    private PermissionContext() {
    }

    public static void setAdmin(boolean isAdmin) {
        AuthorizationContext.setAdmin(isAdmin);
    }

    public static boolean isAdmin() {
        Boolean admin = SecurityContextBridge.isAdminOrNull();
        return admin != null ? admin : AuthorizationContext.isAdmin();
    }

    public static void remove() {
        AuthorizationContext.clear();
    }
}
