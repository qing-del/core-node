package com.jacolp.note.infrastructure.cache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 访客公开接口缓存失效注解。
 *
 * <p>当前只用于笔记公开/下架这类会改变访客可见集合的操作。
 * 采用整组清空策略，避免维护复杂的列表页反向索引。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GuestCacheEvict {

    /**
     * 需要清理的逻辑缓存名。
     */
    String[] cacheNames();

    /**
     * 是否清空整个逻辑缓存。
     */
    boolean allEntries() default true;
}
