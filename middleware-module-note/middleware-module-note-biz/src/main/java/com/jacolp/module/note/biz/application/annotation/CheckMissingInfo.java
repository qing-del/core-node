package com.jacolp.module.note.biz.application.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 该注解有以下功能：
 * <ol>
 *     <li>可以开启事务 -- 使用 {@code enableTransaction} 默认 false</li>
 *     <li>自动检查笔记的缺失信息</li>
 * </ol>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckMissingInfo {
    boolean enableTransaction() default false;
}
