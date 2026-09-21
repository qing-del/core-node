package com.jacolp.document.websocket;

import com.jacolp.document.application.access.DocumentAccess;
import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.websocket.protocol.DocumentWsBinaryFrame;
import com.jacolp.document.websocket.protocol.DocumentWsFrameType;
import java.util.Objects;
import java.util.Optional;
import org.springframework.web.socket.WebSocketSession;

/** 单个会话的运行时元数据；JVM 内只保留最新 Awareness 帧，不保存正文或更新历史。 */
public final class DocumentSessionContext {

    private final WebSocketSession session;
    private final long userId;
    private final String username;
    private final String cursorColor;
    private final long awarenessClientId;
    private volatile SessionAccess access;
    private volatile DocumentSessionSyncStatus syncStatus = DocumentSessionSyncStatus.SYNCING;
    private volatile DocumentWsBinaryFrame latestAwarenessFrame;

    /** 创建尚未完成 bootstrap 的同步中会话上下文。 */
    DocumentSessionContext(WebSocketSession session, long userId, String username, String cursorColor,
                           long awarenessClientId, DocumentAccess documentAccess) {
        this.session = Objects.requireNonNull(session, "session must not be null");
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        this.userId = userId;
        this.username = username;
        if (cursorColor == null || cursorColor.isBlank()) {
            throw new IllegalArgumentException("cursorColor must not be blank");
        }
        this.cursorColor = cursorColor;
        if (awarenessClientId <= 0) {
            throw new IllegalArgumentException("awarenessClientId must be positive");
        }
        this.awarenessClientId = awarenessClientId;
        this.access = toSessionAccess(Objects.requireNonNull(documentAccess, "documentAccess must not be null"));
    }

    /** 返回底层 WebSocket 会话 ID。 */
    public String sessionId() {
        return session.getId();
    }

    /** 返回受 Room 保护的 WebSocket 会话。 */
    public WebSocketSession session() {
        return session;
    }

    /** 返回握手认证得到的用户 ID。 */
    public long userId() {
        return userId;
    }

    /** 返回握手认证得到的用户名；后续 Awareness 元数据将复用该显示名称。 */
    public String username() {
        return username;
    }

    /** 返回服务端为当前 WebSocket Session 分配的临时光标颜色。 */
    public String cursorColor() {
        return cursorColor;
    }

    /** 返回当前 Yjs Awareness 实例的 client ID；同一 Room 内必须唯一。 */
    public long awarenessClientId() {
        return awarenessClientId;
    }

    /** 返回当前会话在最近一次 ACL 校验中获得的文档权限。 */
    public DocumentPermission permission() {
        return access.permission();
    }

    /** 返回当前会话是否为文档所有者。 */
    public boolean owner() {
        return access.owner();
    }

    /** 返回当前会话是否允许提交 CRDT 更新。 */
    public boolean canWrite() {
        SessionAccess current = access;
        return current.owner() || current.permission().canWrite();
    }

    /** 用重复 JOIN 或写入前的最新 ACL 结果刷新会话能力。 */
    void updateAccess(DocumentAccess documentAccess) {
        access = toSessionAccess(Objects.requireNonNull(documentAccess, "documentAccess must not be null"));
    }

    /** 替换当前会话的最新 Awareness 帧；帧本身只允许携带透明的 Awareness 字节。 */
    void updateLatestAwareness(DocumentWsBinaryFrame awarenessFrame) {
        DocumentWsBinaryFrame frame = Objects.requireNonNull(awarenessFrame, "awarenessFrame must not be null");
        if (frame.type() != DocumentWsFrameType.AWARENESS) {
            throw new IllegalArgumentException("awareness frame type must be AWARENESS");
        }
        latestAwarenessFrame = frame;
    }

    /** 返回当前最新 Awareness 帧的不可变快照；尚未发送时返回空。 */
    Optional<DocumentWsBinaryFrame> latestAwareness() {
        return Optional.ofNullable(latestAwarenessFrame);
    }

    /** 在 Session 离开 Room 时清除 Awareness 帧，避免已移除会话继续占用内存。 */
    void clearLatestAwareness() {
        latestAwarenessFrame = null;
    }

    /** 返回 bootstrap 同步状态。 */
    public DocumentSessionSyncStatus syncStatus() {
        return syncStatus;
    }

    /** 将会话从 SYNCING 切换为 ACTIVE。 */
    void markActive() {
        syncStatus = DocumentSessionSyncStatus.ACTIVE;
    }

    private static SessionAccess toSessionAccess(DocumentAccess documentAccess) {
        return new SessionAccess(documentAccess.permission(), documentAccess.owner());
    }

    private record SessionAccess(DocumentPermission permission, boolean owner) {
    }
}
