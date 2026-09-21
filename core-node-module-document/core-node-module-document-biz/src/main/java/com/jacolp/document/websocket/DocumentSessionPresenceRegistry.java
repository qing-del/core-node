package com.jacolp.document.websocket;

import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 通过 Redis TTL 租约让其他 core 节点上的 CLOSE 消费者也能看到 WebSocket 在线状态。 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentSessionPresenceRegistry {

    private final String instanceToken = UUID.randomUUID().toString();
    private final ConcurrentHashMap<String, String> localPresenceKeys = new ConcurrentHashMap<>();
    private final DocumentRedisRepository documentRedisRepository;
    private final DocumentProperties properties;

    /** 创建使用实例令牌区分不同 core 节点的 presence 注册器。 */
    public DocumentSessionPresenceRegistry(DocumentRedisRepository documentRedisRepository,
                                           DocumentProperties properties) {
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository, "documentRedisRepository must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /** 为会话创建或刷新带 TTL 的跨实例 presence 租约。 */
    public void register(long documentId, String sessionId) {
        if (documentId <= 0 || sessionId == null || sessionId.isBlank()) {
            // presence key 依赖这两个值；缺失值会造成无法统计或误删其他会话的租约。
            throw new IllegalArgumentException("documentId and sessionId are required for presence");
        }
        String newKey = "document:presence:%d:%s:%s".formatted(documentId, instanceToken, sessionId);
        String previous = localPresenceKeys.put(sessionId, newKey);
        if (previous != null && !previous.equals(newKey)) {
            // 同一 session 若被重新登记，先删除旧 key，避免一次连接被跨实例统计两次。
            documentRedisRepository.deletePresence(previous);
        }
        documentRedisRepository.savePresence(newKey, leaseTtlMs());
    }

    /** 主动删除会话 presence；连接异常时也可安全重复调用。 */
    public void unregister(String sessionId) {
        if (sessionId == null) {
            // 连接清理回调允许重复触发，空 ID 不对应可删除的 presence。
            return;
        }
        String presenceKey = localPresenceKeys.remove(sessionId);
        if (presenceKey != null) {
            // 只有本 JVM 记录过的 key 才能主动删除，避免误删其他实例的租约。
            documentRedisRepository.deletePresence(presenceKey);
        }
    }

    /** 统计指定文档当前仍未过期的跨实例 presence。 */
    public long count(long documentId) {
        return documentRedisRepository.countPresence(documentId);
    }

    /** 定期续期当前 JVM 仍持有的所有会话租约。 */
    @Scheduled(fixedDelayString = "${jacolp.document.session-presence-refresh-ms:10000}")
    public void refreshLocalLeases() {
        long ttl = leaseTtlMs();
        localPresenceKeys.values().forEach(key -> documentRedisRepository.savePresence(key, ttl));
    }

    /** 计算至少覆盖两个关闭延迟周期的租约时长。 */
    private long leaseTtlMs() {
        long refresh = properties.getSessionPresenceRefreshMs();
        if (refresh <= 0) {
            // 刷新周期无效时无法保证租约续期，继续运行会让 CLOSE 错误地看不到在线会话。
            throw new IllegalStateException("jacolp.document.session-presence-refresh-ms must be positive");
        }
        return Math.max(properties.getCloseDelayMs() * 2L, refresh * 3L);
    }
}
