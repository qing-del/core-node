package com.jacolp.module.note.biz.infrastructure.cache;

/**
 * 通用缓存操作抽象。
 *
 * <p>业务和 AOP 只依赖这个接口，当前实现是本地 Caffeine，后续如需切换 Redis、
 * 多级缓存或分布式失效，只替换实现类即可。</p>
 */
public interface CacheOperator {

    /**
     * 缓存未命中时执行的回源函数。
     *
     * <p>允许抛出 {@link Throwable}，用于完整透传被切方法的业务异常。</p>
     */
    @FunctionalInterface
    interface CacheLoader<T> {
        T load() throws Throwable;
    }

    /**
     * 获取缓存值；未命中时调用 loader 回源并写入缓存。
     *
     * <p>实现类应保证同一个 {@code cacheName + key} 同时只有一个线程回源，
     * 其他线程等待并复用回源结果，避免热点 key 过期瞬间击穿数据库。</p>
     *
     * @param cacheName 逻辑缓存名
     * @param key       缓存 key
     * @param loader    缓存未命中时的回源逻辑
     * @param ttlSeconds 过期时间，单位秒；实现类可按缓存名覆盖默认值
     * @param <T>       缓存值类型
     * @return 缓存值或回源结果
     * @throws Throwable 回源逻辑抛出的业务异常
     */
    <T> T get(String cacheName, String key, CacheLoader<T> loader, long ttlSeconds) throws Throwable;

    /**
     * 删除指定 key。
     *
     * @param cacheName 逻辑缓存名
     * @param key       缓存 key
     */
    void evict(String cacheName, String key);

    /**
     * 清空指定逻辑缓存。
     *
     * @param cacheName 逻辑缓存名
     */
    void clear(String cacheName);

    /**
     * 返回指定逻辑缓存的估算条目数。
     *
     * @param cacheName 逻辑缓存名
     * @return 估算条目数，缓存不存在时返回 0
     */
    long estimatedSize(String cacheName);
}
