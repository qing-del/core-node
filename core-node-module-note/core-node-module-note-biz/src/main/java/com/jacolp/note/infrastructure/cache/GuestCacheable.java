package com.jacolp.note.infrastructure.cache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 访客公开接口读缓存注解。
 *
 * <p>仅用于公开、只读、无登录上下文依赖的方法。异常结果不会被缓存，
 * 因此未公开或不存在的笔记仍保持原业务语义。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GuestCacheable {

    /**
     * 逻辑缓存名。
     */
    String cacheName();

    /**
     * 缓存 TTL，单位秒。
     */
    long ttlSeconds() default 300L;
}
