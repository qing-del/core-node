package com.jacolp.system.application.provider;

/**
 * 标记接口：实现此接口的 DTO 表示其携带了待校验的角色 ID。
 * 用于 AOP 在方法执行前统一做角色合法性校验。
 */
public interface RoleIdProvider {
    Long getRoleId();
}
