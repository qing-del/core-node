package com.jacolp.system.infrastructure.concurrent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * 锁操作类
 * <p>单机类<b>不可重入</b>的<b>非公平</b>锁</p>
 * <p>属于非公平锁，可能导致“饥饿”问题</p>
 */
@Component
@Slf4j
public class LockOperator {

    private static final long DEFAULT_SIGNAL_TIMEOUT = 200;

    // 存储锁的持有者
    private static final ConcurrentHashMap<String, String> LOCK_MAP = new ConcurrentHashMap<>();

    // 使用 ConcurrentLinkedQueue 作为并发等待队列，支持多个线程同时排队
    private static final ConcurrentHashMap<String, ConcurrentLinkedQueue<Thread>> WAIT_MAP = new ConcurrentHashMap<>();

    /**
     * 尝试获取<b>不可重入</b>锁
     * <p>这里不做等待，获取失败直接返回 false</p>
     * @param key   锁的 key
     * @param owner 锁的持有者
     * @return 是否成功获取锁
     */
    public boolean tryLock(String key, String owner) {
        return LOCK_MAP.putIfAbsent(key, owner) == null;
    }

    /**
     * 尝试获取<b>不可重入</b>锁
     * <p>这里会等待，获取失败会返回 false</p>
     * @param key   锁的 key
     * @param owner 锁的持有者（建议使用 UUID 来生成，并且保存好身份识别）
     * @param timeoutMillis 等待超时时间，单位毫秒
     * @return 是否成功获取锁
     */
    public boolean tryLock(String key, String owner, long timeoutMillis) {
        // 首次尝试快速获取锁
        if (tryLock(key, owner)) {
            return true;
        }
        if (timeoutMillis <= 0) {
            return false;
        }

        // 使用 deadline 减少计算偏差积累
        long deadline = System.currentTimeMillis() + timeoutMillis;
        Thread currentThread = Thread.currentThread();

        // 将当前线程安全地加入该 key 的等待队列
        ConcurrentLinkedQueue<Thread> waitQueue = WAIT_MAP.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
        waitQueue.add(currentThread);

        try {
            while (true) {
                // 进入队列之后，再次尝试获取锁，防止发生无效阻塞
                if (tryLock(key, owner)) {
                    return true;
                }

                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return false;
                }

                // 限时阻塞当前线程
                LockSupport.parkNanos(this, TimeUnit.MILLISECONDS.toNanos(remaining));  // 防止 < 0

                // 可打断
                if (Thread.currentThread().isInterrupted()) {
                    return false;
                }
            }
        } finally {
            // 修复内存泄漏：无论成功、超时还是中断，都必须将自己从队列中移除
            waitQueue.remove(currentThread);

            // 如果队列空了，顺手清理掉 map 中的 key
            if (waitQueue.isEmpty()) {
                WAIT_MAP.computeIfPresent(key, (k, q) -> q.isEmpty() ? null : q);
            }
        }
    }

    /**
     * 释放锁
     * @param key   锁的 key
     * @param owner 锁的持有者
     * @return 是否成功释放锁
     */
    public boolean releaseLock(String key, String owner) {
        // 利用 ConcurrentHashMap 提供的原子方法 remove(key, value)
        if (!LOCK_MAP.remove(key, owner)) {
            // 使用 warn，因为释放非自己持有的锁可能只是业务逻辑重复调用
            log.warn("releaseLock failed! Key not found or not owned by current owner. Key: {}, Owner: {}", key, owner);
            return false;
        }

        // 获取等待队列并唤醒排在前面的有效线程
        ConcurrentLinkedQueue<Thread> waitQueue = WAIT_MAP.get(key);
        if (waitQueue != null) {
            wakeUpNext(waitQueue);
        }
        return true;
    }

    /**
     * 批量获取锁（内部保证全局固定顺序，防止死锁）
     * <p>- 单个锁最大等待时间可以参考 {@link LockOperator#DEFAULT_SIGNAL_TIMEOUT}</p>
     * @param keys          锁的 key 集合
     * @param owner         锁持有者标识
     * @param timeoutMillis 整个批量操作的超时时间（毫秒）
     * @return 如果全部获取成功，返回已获取的 key 列表；否则返回 null（已自动释放已获取的锁）
     */
    public List<String> tryLockBatch(Collection<String> keys, String owner, long timeoutMillis) {
        return tryLockBatch(keys, owner, timeoutMillis, DEFAULT_SIGNAL_TIMEOUT);
    }

    /**
     * 批量获取锁（内部保证全局固定顺序，防止死锁）。
     * @param keys          锁的 key 集合
     * @param owner         锁持有者标识
     * @param timeoutMillis 整个批量操作的超时时间（毫秒）
     * @param signalTimeoutMillis 获取单个锁的超时时间（毫秒）
     * @return 如果全部获取成功，返回已获取的 key 列表；否则返回 null（已自动释放已获取的锁）
     */
    public List<String> tryLockBatch(Collection<String> keys, String owner, long timeoutMillis, long signalTimeoutMillis) {
        // 排序 key，防止死锁
        List<String> sortedKeys = new ArrayList<>(keys);
        Collections.sort(sortedKeys);

        List<String> acquiredKeys = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeoutMillis;

        for (String key : sortedKeys) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                releaseBatch(acquiredKeys, owner);
                return null;
            }

            // 每个单独的锁给一个上限（比如 200ms），防止单个 key 占用过长时间
            if (!tryLock(key, owner, Math.min(remaining, signalTimeoutMillis))) {
                releaseBatch(acquiredKeys, owner);
                return null;
            }
            acquiredKeys.add(key);
        }
        return acquiredKeys;
    }

    /**
     * 批量释放锁。
     */
    public void releaseBatch(Collection<String> keys, String owner) {
        for (String key : keys) {
            releaseLock(key, owner);
        }
    }

    /**
     * 唤醒等待队列中第一个存活的线程，并清理已终止的僵尸节点。
     */
    private void wakeUpNext(ConcurrentLinkedQueue<Thread> waitQueue) {
        Thread toUnpark;
        while ((toUnpark = waitQueue.peek()) != null) {
            if (toUnpark.isAlive() && !toUnpark.isInterrupted()) {
                LockSupport.unpark(toUnpark);
                return; // 每次只唤醒队头一个，让其竞争后再决定是否唤醒下一个
            }
            // 线程已终止，清理僵尸节点后继续寻找
            waitQueue.poll();
        }
    }
}
