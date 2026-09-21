package com.jacolp.document.application.close;

import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.DocumentRoomMeta;
import com.jacolp.document.messaging.DocumentSchedulePublisher;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 在 JVM 本地 Room 之外维护可持久化的关闭令牌状态。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentRoomLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(DocumentRoomLifecycleService.class);
    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Shanghai");

    private final DocumentRedisRepository documentRedisRepository;
    private final DocumentSchedulePublisher schedulePublisher;

    /** 注入 Redis 运行态存储与文档调度发布器。 */
    public DocumentRoomLifecycleService(DocumentRedisRepository documentRedisRepository,
                                        DocumentSchedulePublisher schedulePublisher) {
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository, "documentRedisRepository must not be null");
        this.schedulePublisher = Objects.requireNonNull(schedulePublisher, "schedulePublisher must not be null");
    }

    /** JOIN 会在 bootstrap 完成前使所有旧的延迟 CLOSE 令牌失效。 */
    public void reopen(DocumentDO document, long lastModifyUserId) {
        long lastModifiedAt = document.getLastModifyTime() == null ? System.currentTimeMillis()
                : document.getLastModifyTime().atZone(APPLICATION_ZONE).toInstant().toEpochMilli();
        documentRedisRepository.saveRoomMeta(new DocumentRoomMeta(document.getId(), document.getOwnerUserId(), false,
                UUID.randomUUID().toString(), lastModifiedAt, lastModifyUserId));
    }

    /** 本机最后一个会话离开即可请求关闭；消费者会通过全局在线状态决定是否真正执行。 */
    public void requestClose(long documentId, long ownerUserId) {
        DocumentRoomMeta previous = documentRedisRepository.findRoomMeta(documentId)
                .orElse(new DocumentRoomMeta(documentId, ownerUserId, false, null, System.currentTimeMillis(), ownerUserId));
        if (previous.ownerUserId() != ownerUserId) {
            // 关闭请求不能覆盖其他所有者的 Room Meta，避免错误地关闭无关文档运行态。
            throw new IllegalStateException("document room meta owner does not match close requester");
        }
        String closeToken = UUID.randomUUID().toString();
        documentRedisRepository.saveRoomMeta(new DocumentRoomMeta(documentId, ownerUserId, true, closeToken,
                previous.lastModifyTime(), previous.lastModifyUserId()));
        try {
            schedulePublisher.scheduleClose(documentId, closeToken);
        } catch (RuntimeException exception) {
            log.warn("Could not schedule CLOSE for documentId={}: {}", documentId, exception.getMessage());
        }
    }

    /** 判断 Redis 中的关闭请求是否仍与当前令牌一致。 */
    public boolean isCurrentClose(long documentId, String closeToken) {
        return closeToken != null && documentRedisRepository.findRoomMeta(documentId)
                .map(meta -> meta.closeRequested() && closeToken.equals(meta.closeToken()))
                .orElse(false);
    }

    /** 定时恢复仍处于关闭请求状态但可能丢失调度消息的 Room。 */
    @Scheduled(fixedDelayString = "${jacolp.document.flush-log.recovery-scan-ms:30000}")
    public void rescheduleOutstandingCloses() {
        for (DocumentRoomMeta meta : documentRedisRepository.findRoomMetas()) {
            if (!meta.closeRequested() || meta.closeToken() == null) {
                // 只有仍处于关闭窗口且带有效令牌的 Meta 才需要补发 CLOSE，正常 Room 不重复调度。
                continue;
            }
            try {
                schedulePublisher.scheduleClose(meta.documentId(), meta.closeToken());
            } catch (RuntimeException exception) {
                log.warn("Could not recover CLOSE schedule for documentId={}: {}", meta.documentId(), exception.getMessage());
            }
        }
    }
}
