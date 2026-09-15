package com.jacolp.module.system.biz.application.annotation;

import com.jacolp.module.system.biz.application.provider.TargetUserProvider;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注此注解的方法在执行前会进行权限校验：
 * 1. 操作者的 roleId 必须 <= 2（即创建者或管理员）。
 * 2. 操作者的 roleId 必须严格小于被操作用户的 roleId。
 * <p>
 * 被标注的方法参数中必须包含一个实现了 {@link TargetUserProvider} 接口的参数，
 * 否则会在运行期抛出开发者级别的异常。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireSuperiorRole {
}
