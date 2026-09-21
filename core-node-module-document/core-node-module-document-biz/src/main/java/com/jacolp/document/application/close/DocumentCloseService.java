package com.jacolp.document.application.close;

import com.jacolp.document.application.compact.DocumentCompactResult;
import com.jacolp.document.application.compact.DocumentCompactService;
import com.jacolp.document.application.binding.DocumentBindingConsumeResult;
import com.jacolp.document.application.binding.DocumentBindingConsumer;
import com.jacolp.document.application.flush.DocumentFlushLogResult;
import com.jacolp.document.application.flush.DocumentFlushLogService;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.metrics.DocumentMetrics;
import com.jacolp.document.websocket.DocumentRoomManager;
import com.jacolp.document.websocket.DocumentSessionPresenceRegistry;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 执行最终 FLUSH_LOG 与 COMPACT，并在两次关闭校验都通过后清理运行时状态。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentCloseService {

    private final DocumentRoomLifecycleService lifecycleService;
    private final DocumentSessionPresenceRegistry presenceRegistry;
    private final DocumentRoomManager roomManager;
    private final DocumentFlushLogService flushLogService;
    private final DocumentBindingConsumer bindingConsumer;
    private final DocumentCompactService compactService;
    private final DocumentRedisRepository documentRedisRepository;
    private final DocumentMetrics metrics;

    /** 创建不依赖指标实现的关闭服务，便于轻量测试和非监控调用方装配。 */
    public DocumentCloseService(DocumentRoomLifecycleService lifecycleService,
                                DocumentSessionPresenceRegistry presenceRegistry,
                                DocumentRoomManager roomManager,
                                DocumentFlushLogService flushLogService,
                                DocumentBindingConsumer bindingConsumer,
                                DocumentCompactService compactService,
                                DocumentRedisRepository documentRedisRepository) {
        this(lifecycleService, presenceRegistry, roomManager, flushLogService, bindingConsumer, compactService,
                documentRedisRepository, DocumentMetrics.noop());
    }

    /** 创建带指标记录能力的最终关闭服务。 */
    @Autowired
    public DocumentCloseService(DocumentRoomLifecycleService lifecycleService,
                                DocumentSessionPresenceRegistry presenceRegistry,
                                DocumentRoomManager roomManager,
                                DocumentFlushLogService flushLogService,
                                DocumentBindingConsumer bindingConsumer,
                                DocumentCompactService compactService,
                                DocumentRedisRepository documentRedisRepository, DocumentMetrics metrics) {
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService must not be null");
        this.presenceRegistry = Objects.requireNonNull(presenceRegistry, "presenceRegistry must not be null");
        this.roomManager = Objects.requireNonNull(roomManager, "roomManager must not be null");
        this.flushLogService = Objects.requireNonNull(flushLogService, "flushLogService must not be null");
        this.bindingConsumer = Objects.requireNonNull(bindingConsumer, "bindingConsumer must not be null");
        this.compactService = Objects.requireNonNull(compactService, "compactService must not be null");
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository, "documentRedisRepository must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /** 在令牌、全局 presence 和本机 Room 均允许时执行最终刷盘、压缩和运行态清理。 */
    public DocumentCloseResult close(long documentId, String closeToken) {
        try {
            // 关闭消息可能延迟、重复或已经过期，先用 Redis 令牌和在线状态做幂等闸门。
            if (!closeGuardsPass(documentId, closeToken) || !roomManager.beginClosingIfEmpty(documentId)) {
                return new DocumentCloseResult(documentId, DocumentCloseResult.Status.IGNORED);
            }
            flushAll(documentId);
            compactAll(documentId);
            // 持久化可能耗时较长，因此再次检查：若期间有会话重连，就撤销本次关闭，
            // 保留 Room 与 Redis 运行时状态供新会话继续使用。
            if (!closeGuardsPass(documentId, closeToken) || !roomManager.hasNoLocalSessions(documentId)
                    || documentRedisRepository.pendingUpdateCount(documentId) > 0
                    || documentRedisRepository.pendingBindingCount(documentId) > 0) {
                return new DocumentCloseResult(documentId, DocumentCloseResult.Status.REOPENED);
            }
            roomManager.removeIfEmpty(documentId);
            documentRedisRepository.deleteRoomRuntime(documentId);
            return new DocumentCloseResult(documentId, DocumentCloseResult.Status.CLOSED);
        } catch (RuntimeException exception) {
            metrics.recordCloseFailed();
            throw exception;
        }
    }

    /** 校验当前关闭令牌仍有效，且跨节点与本机都没有存活会话。 */
    private boolean closeGuardsPass(long documentId, String closeToken) {
        return lifecycleService.isCurrentClose(documentId, closeToken) && presenceRegistry.count(documentId) == 0
                && roomManager.hasNoLocalSessions(documentId);
    }

    /** 持续刷盘直到 Redis Stream 不再有待处理更新。 */
    private void flushAll(long documentId) {
        DocumentFlushLogResult result;
        DocumentBindingConsumeResult bindingResult;
        do {
            result = flushLogService.flush(documentId);
            // 固定先推进 updates，再用同一个单例消费者排空 Binding；任一 Stream 还有处理量都继续轮询。
            bindingResult = bindingConsumer.drain(documentId);
        } while (result.processedCount() > 0 || bindingResult.processedCount() > 0);
    }

    /** 持续压缩直到当前持久化日志没有可合并内容。 */
    private void compactAll(long documentId) {
        while (true) {
            DocumentCompactResult result = compactService.compact(documentId);
            if (result.status() == DocumentCompactResult.Status.NO_UPDATES) {
                // NO_UPDATES 表示当前持久化日志都已纳入 Snapshot，最终关闭可以继续清理运行态。
                return;
            }
        }
    }
}
