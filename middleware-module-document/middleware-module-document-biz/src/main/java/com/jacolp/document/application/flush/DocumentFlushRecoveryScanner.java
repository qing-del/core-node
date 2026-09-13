package com.jacolp.document.application.flush;

import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.messaging.DocumentSchedulePublisher;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 发布器失败或 Broker 丢失消息后重新发送 flush 信号；Redis 仍是待写入数据的事实来源。 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentFlushRecoveryScanner {

    private static final Logger log = LoggerFactory.getLogger(DocumentFlushRecoveryScanner.class);

    private final DocumentRedisRepository documentRedisRepository;
    private final DocumentSchedulePublisher schedulePublisher;

    /** 创建依赖 Redis 事实状态的恢复扫描器。 */
    public DocumentFlushRecoveryScanner(DocumentRedisRepository documentRedisRepository,
                                        DocumentSchedulePublisher schedulePublisher) {
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository, "documentRedisRepository must not be null");
        this.schedulePublisher = Objects.requireNonNull(schedulePublisher, "schedulePublisher must not be null");
    }

    /** 扫描仍有待刷盘更新的 Room，并重新发布轻量 FLUSH_LOG 信号。 */
    @Scheduled(fixedDelayString = "${jacolp.document.flush-log.recovery-scan-ms:30000}")
    public void scanAndReschedule() {
        for (var meta : documentRedisRepository.findRoomMetas()) {
            try {
                boolean hasPendingUpdates = documentRedisRepository.pendingUpdateCount(meta.documentId()) > 0;
                boolean hasPendingBindings = documentRedisRepository.pendingBindingCount(meta.documentId()) > 0;
                if (hasPendingUpdates || hasPendingBindings) {
                    schedulePublisher.scheduleFlushLog(meta.documentId());
                }
            } catch (RuntimeException exception) {
                log.warn("Could not recover FLUSH_LOG schedule for documentId={}: {}", meta.documentId(),
                        exception.getMessage());
            }
        }
    }
}
