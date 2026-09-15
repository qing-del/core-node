package com.jacolp.constant;

/**
 * 作用域常量。
 *
 * <p>用于控制用户端资源查询的个人/全局范围。</p>
 */
public class ScopeConstant {

    /**
     * 全局作用域 — 用户自己的资源 + 别人公开/已通过审核的资源。
     */
    public static final String SCOPE_GLOBAL = "global";

    /**
     * 个人作用域 — 仅查询当前用户自己的资源（默认）。
     */
    public static final String SCOPE_PERSONAL = "personal";

    private ScopeConstant() {
        // 工具类禁止实例化
    }
}
