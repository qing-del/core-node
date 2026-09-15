package com.jacolp.middleware.common.security.context;

public final class AuthorizationContext {

    private static final ThreadLocal<Boolean> ADMIN = new ThreadLocal<>();

    private AuthorizationContext() {
    }

    public static void setAdmin(boolean admin) {
        ADMIN.set(admin);
    }

    public static boolean isAdmin() {
        return Boolean.TRUE.equals(ADMIN.get());
    }

    public static void clear() {
        ADMIN.remove();
    }
}
