package com.jacolp.system.application.provider;

/**
 * 标记接口：实现此接口的 DTO 表示其携带了一个"被操作的目标用户 ID"。
 * 用于 AOP 切面 {@code SuperiorRoleAspect} 自动提取目标用户 ID 做权限比对。
 */
public interface TargetUserProvider {
    Long getTargetUserId();
}
