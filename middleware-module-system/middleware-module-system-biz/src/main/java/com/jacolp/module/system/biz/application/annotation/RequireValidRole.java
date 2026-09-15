package com.jacolp.module.system.biz.application.annotation;

import com.jacolp.module.system.biz.application.provider.RoleIdProvider;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注此注解的方法在执行前会进行角色合法性校验。
 * 被标注的方法参数中必须包含一个实现了 {@link RoleIdProvider} 的参数。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireValidRole {

    /**
     * 角色是否必填。
     * 新增用户通常为 true；更新用户可设为 false（未传角色则跳过校验）。
     */
    boolean required() default true;
}
