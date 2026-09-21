package com.jacolp.document.websocket;

import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.metrics.DocumentMetrics;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.jacolp.document.websocket.exception.DocumentRoomAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 管理 JVM 本地 Room 运行时状态；可恢复内容仍存放在 Redis、MySQL 与 MinIO。 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentRoomManager {

    private final DocumentProperties properties;
    private final DocumentMetrics metrics;
    private final ConcurrentHashMap<Long, DocumentRoom> rooms = new ConcurrentHashMap<>();

    /** 创建不记录指标的本机 Room 管理器。 */
    public DocumentRoomManager(DocumentProperties properties) {
        this(properties, DocumentMetrics.noop());
    }

    /** 创建带运行态指标更新能力的本机 Room 管理器。 */
    @Autowired
    public DocumentRoomManager(DocumentProperties properties, DocumentMetrics metrics) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /** 按文档和所有者获取或创建本机 Room；调用方负责先完成文档 ACL 校验。 */
    public DocumentRoom getOrCreate(long documentId, long ownerUserId) {
        return rooms.compute(documentId, (ignored, existing) -> {
            if (existing == null) {
                // 仅在本机首次看到该文档时创建运行态 Room，正文仍从持久化层 bootstrap。
                return new DocumentRoom(documentId, ownerUserId, properties);
            }
            if (existing.ownerUserId() != ownerUserId) {
                // 同一个文档 ID 不能在本 JVM 内被重新绑定到另一个所有者快照。
                throw new DocumentRoomAccessException("document Room owner does not match");
            }
            return existing;
        });
    }

    /** 查找本机已有 Room；未创建时返回空结果。 */
    public Optional<DocumentRoom> find(long documentId) {
        return Optional.ofNullable(rooms.get(documentId));
    }

    /** 仅在 Room 无会话时移除本机容器中的 Room。 */
    public boolean removeIfEmpty(long documentId) {
        AtomicBoolean removed = new AtomicBoolean();
        rooms.computeIfPresent(documentId, (ignored, room) -> {
            if (room.sessionCount() == 0) {
                // 只删除空 Room；并发 JOIN 若已留下会话，则保留容器避免丢失运行态成员。
                removed.set(true);
                return null;
            }
            return room;
        });
        refreshRuntimeMetrics();
        return removed.get();
    }

    /** 本机没有 Room 只会发生在重启或最终清理后，两种情况都表示本 JVM 没有存活会话。 */
    public boolean hasNoLocalSessions(long documentId) {
        // 没有本机 Room 代表本节点没有活跃会话；跨节点是否在线由 presenceRegistry 另行判断。
        return find(documentId).map(room -> room.sessionCount() == 0).orElse(true);
    }

    /** 请求空 Room 进入 CLOSING；没有本机 Room 时视为可继续。 */
    public boolean beginClosingIfEmpty(long documentId) {
        // 本机没有 Room 时不阻塞 CLOSE，剩余的全局在线状态由 Redis presence 负责裁决。
        return find(documentId).map(DocumentRoom::beginClosingIfEmpty).orElse(true);
    }

    /** 会话归属变化后由处理器调用；指标值仅代表当前 Java 实例。 */
    public void refreshRuntimeMetrics() {
        int sessions = 0;
        int active = 0;
        for (DocumentRoom room : rooms.values()) {
            int roomSessions = room.sessionCount();
            sessions += roomSessions;
            if (roomSessions > 0) active++;
        }
        metrics.updateRuntimeCounts(active, sessions);
    }
}
