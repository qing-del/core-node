package com.jacolp.common.security.context;

import com.jacolp.common.core.exception.AuthenticationException;
import com.jacolp.common.security.context.CurrentPrincipalAccessor;
import com.jacolp.common.security.context.SecurityContextCurrentPrincipalAccessor;

/**
 * 旧业务代码使用的认证上下文兼容入口。
 */
public final class BaseContext {

    private static final CurrentPrincipalAccessor CURRENT_PRINCIPAL_ACCESSOR = new SecurityContextCurrentPrincipalAccessor();

    private BaseContext() {
    }

    public static Long getCurrentId() {
        return CURRENT_PRINCIPAL_ACCESSOR.currentPrincipal()
                .map(principal -> principal.userId())
                .orElseThrow(() -> new AuthenticationException("当前登录信息已失效"));
    }

    public static Long getCurrentIdWithoutValid() {
        return CURRENT_PRINCIPAL_ACCESSOR.currentPrincipal().map(principal -> principal.userId()).orElse(null);
    }
}
