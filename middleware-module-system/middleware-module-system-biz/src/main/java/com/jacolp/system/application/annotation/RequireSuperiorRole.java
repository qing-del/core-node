package com.jacolp.system.application.annotation;

import com.jacolp.system.application.provider.TargetUserProvider;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注此注解的方法在执行前会进行权限校验：
 * 1. 操作者必须具有 CREATOR 或 ADMIN 管理身份。
 * 2. 操作者必须在 {@code sys_role.rank} 中严格高于被操作用户（数值越小等级越高）。
 * <p>
 * 被标注的方法参数中必须包含一个实现了 {@link TargetUserProvider} 接口的参数，
 * 否则会在运行期抛出开发者级别的异常。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireSuperiorRole {
}
