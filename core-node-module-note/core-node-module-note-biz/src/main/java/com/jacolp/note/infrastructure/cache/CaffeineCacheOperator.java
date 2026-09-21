package com.jacolp.note.infrastructure.cache;

import static com.jacolp.note.infrastructure.cache.GuestCacheConstant.GUEST_NOTE_DETAIL_CACHE;
import static com.jacolp.note.infrastructure.cache.GuestCacheConstant.GUEST_NOTE_DETAIL_MAX_SIZE;
import static com.jacolp.note.infrastructure.cache.GuestCacheConstant.GUEST_NOTE_DETAIL_TTL_SECONDS;
import static com.jacolp.note.infrastructure.cache.GuestCacheConstant.GUEST_NOTE_LIST_CACHE;
import static com.jacolp.note.infrastructure.cache.GuestCacheConstant.GUEST_NOTE_LIST_MAX_SIZE;
import static com.jacolp.note.infrastructure.cache.GuestCacheConstant.GUEST_NOTE_LIST_TTL_SECONDS;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Caffeine 的本地缓存实现。
 *
 * <p>该类是 {@link CacheOperator} 的当前默认实现。它按 cacheName 维护多组
 * Caffeine Cache，并通过 {@code loadingMap} 对同一个 key 做单航班回源保护：
 * 第一个线程负责查询数据库，后续并发线程等待同一个 {@link CompletableFuture}
 * 的结果。</p>
 *
 * <p>注意：这是单机本地缓存。如果后续部署多实例，需要提供 Redis 或消息广播版
 * {@link CacheOperator} 来处理跨实例一致性。</p>
 */
@Component
@Slf4j
public class CaffeineCacheOperator implements CacheOperator {

    private static final long DEFAULT_MAX_SIZE = 1000L;
    private static final long DEFAULT_TTL_SECONDS = 300L;

    /** cacheName -> Caffeine Cache。 */
    private final ConcurrentMap<String, Cache<String, Object>> caches = new ConcurrentHashMap<>();

    /** 正在回源的 key，用于防止热点缓存击穿。 */
    private final ConcurrentMap<String, CompletableFuture<Object>> loadingMap = new ConcurrentHashMap<>();

    /** cacheName -> 版本号；清空缓存时递增，用于阻止旧回源结果重新写入。 */
    private final ConcurrentMap<String, AtomicLong> cacheVersions = new ConcurrentHashMap<>();

    /**
     * 读取缓存；未命中时对同 key 做单航班回源。
     *
     * <p>如果回源期间发生 {@link #clear(String)}，回源完成后不会把旧结果写回缓存，
     * 但仍会把本次查询结果返回给当前请求。</p>
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key, CacheLoader<T> loader, long ttlSeconds) throws Throwable {
        // 参数校验
        validateCacheArgs(cacheName, key);
        // 获取对应的 Caffeine Cache 集合
        Cache<String, Object> cache = getCache(cacheName, ttlSeconds);

        // 尝试从缓存中获取
        Object cached = cache.getIfPresent(key);
        if (cached != null) {
            log.debug("Guest cache hit, cacheName: {}, key: {}", cacheName, key);
            return (T) cached;
        }

        // 先尝试将自己变成“回源线程”
        String loadingKey = cacheName + "::" + key;
        CompletableFuture<Object> currentLoader = new CompletableFuture<>();
        CompletableFuture<Object> existedLoader = loadingMap.putIfAbsent(loadingKey, currentLoader);
        // 如果尝试失败，则说明已经有其他线程在回源，等待结果
        if (existedLoader != null) {
            log.debug("Guest cache waiting for loading, cacheName: {}, key: {}", cacheName, key);
            return (T) waitForLoadedValue(existedLoader);
        }

        try {
            // 成为回源线程后再次检查缓存，避免前一个回源线程刚好完成造成重复查询（DCL策略）
            Object doubleChecked = cache.getIfPresent(key);
            if (doubleChecked != null) {
                currentLoader.complete(doubleChecked);  // 告诉等待的线程结果已就绪
                return (T) doubleChecked;
            }

            // 记录回源前版本。若中途缓存被清空，版本会变化，旧值不再写入缓存。
            long versionBeforeLoad = getCacheVersion(cacheName).get();
            log.debug("Guest cache miss, cacheName: {}, key: {}", cacheName, key);
            T loaded = loader.load();
            if (loaded != null && versionBeforeLoad == getCacheVersion(cacheName).get()) {
                cache.put(key, loaded);
            }

            // 通知其他正在等待的线程
            currentLoader.complete(loaded);

            // 返回结果
            return loaded;
        } catch (Throwable e) {
            // 回源异常也要通知等待线程，否则等待同一个 future 的请求会一直卡住
            currentLoader.completeExceptionally(e);
            throw e;
        } finally {
            // 只移除自己创建的 future，避免误删后续新一轮回源任务。
            loadingMap.remove(loadingKey, currentLoader);
        }
    }

    @Override
    public void evict(String cacheName, String key) {
        // 单 key 删除用于后续精确失效扩展；当前访客缓存主要使用 clear。
        if (!StringUtils.hasText(cacheName) || !StringUtils.hasText(key)) {
            return;
        }
        Cache<String, Object> cache = caches.get(cacheName);
        if (cache != null) {
            cache.invalidate(key);
        }
    }

    @Override
    public void clear(String cacheName) {
        // 空缓存名直接忽略，避免调用方误传空值时清理不可预期的缓存。
        if (!StringUtils.hasText(cacheName)) {
            return;
        }
        Cache<String, Object> cache = caches.get(cacheName);
        if (cache != null) {
            // 让正在回源的线程知道“这次回源开始时的缓存视图已经过期”。
            getCacheVersion(cacheName).incrementAndGet();

            // 清空整组缓存，适合公开/下架这类会影响列表和详情的批量可见性变化。
            cache.invalidateAll();

            log.debug("Guest cache cleared, cacheName: {}", cacheName);
        }
    }

    @Override
    public long estimatedSize(String cacheName) {
        Cache<String, Object> cache = caches.get(cacheName);
        return cache == null ? 0L : cache.estimatedSize();
    }

    /**
     * 获取对应的 Caffeine Cache 集合。
     *
     * <p>如果缓存不存在，则创建一个。</p>
     */
    private Cache<String, Object> getCache(String cacheName, long ttlSeconds) {
        return caches.computeIfAbsent(cacheName, name -> {
            CacheSpec spec = resolveSpec(name, ttlSeconds);
            return Caffeine.newBuilder()
                    .expireAfterWrite(Duration.ofSeconds(spec.ttlSeconds()))
                    .maximumSize(spec.maximumSize())
                    .build();
        });
    }

    /**
     * 等待其他线程的同 key 回源结果。
     *
     * <p>这里会展开 {@link ExecutionException}，保持调用方看到的仍是原业务异常。</p>
     */
    private Object waitForLoadedValue(CompletableFuture<Object> existedLoader) throws Throwable {
        try {
            return existedLoader.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    /**
     * 按缓存名解析容量和 TTL。
     *
     * <p>访客列表和详情有独立配置；未知缓存名则使用调用方传入 TTL 和默认容量。</p>
     */
    private CacheSpec resolveSpec(String cacheName, long ttlSeconds) {
        if (GUEST_NOTE_LIST_CACHE.equals(cacheName)) {
            return new CacheSpec(GUEST_NOTE_LIST_TTL_SECONDS, GUEST_NOTE_LIST_MAX_SIZE);
        }
        if (GUEST_NOTE_DETAIL_CACHE.equals(cacheName)) {
            return new CacheSpec(GUEST_NOTE_DETAIL_TTL_SECONDS, GUEST_NOTE_DETAIL_MAX_SIZE);
        }
        long ttl = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
        return new CacheSpec(ttl, DEFAULT_MAX_SIZE);
    }

    /**
     * 缓存参数校验。
     *
     * <p>空 cacheName 或空 key 会造成不同请求错误地共用缓存，因此直接拒绝。</p>
     */
    private void validateCacheArgs(String cacheName, String key) {
        if (!StringUtils.hasText(cacheName)) {
            throw new IllegalArgumentException("cacheName cannot be blank");
        }
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("cache key cannot be blank");
        }
    }

    /**
     * 获取逻辑缓存的版本号。
     */
    private AtomicLong getCacheVersion(String cacheName) {
        return cacheVersions.computeIfAbsent(cacheName, key -> new AtomicLong());
    }

    /**
     * 单个逻辑缓存的本地配置。
     */
    private record CacheSpec(long ttlSeconds, long maximumSize) {
    }
}
