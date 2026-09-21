package com.jacolp.document.websocket;

import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.common.security.oauth2.authorization.PermissionScopeMatcher;
import com.jacolp.document.application.access.DocumentAccess;
import com.jacolp.document.application.access.DocumentAccessDeniedException;
import com.jacolp.document.application.access.DocumentAccessService;
import com.jacolp.document.application.close.DocumentRoomLifecycleService;
import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.redis.DocumentLinkAcceptedRedisOperations;
import com.jacolp.document.infrastructure.redis.DocumentPendingBinding;
import com.jacolp.document.infrastructure.redis.DocumentPendingUpdate;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.DocumentRoomMeta;
import com.jacolp.document.messaging.DocumentSchedulePublisher;
import com.jacolp.document.metrics.DocumentMetrics;
import com.jacolp.document.websocket.exception.DocumentAwarenessException;
import com.jacolp.document.websocket.exception.DocumentBootstrapException;
import com.jacolp.document.websocket.exception.DocumentRoomAccessException;
import com.jacolp.document.websocket.exception.DocumentRoomLimitExceededException;
import com.jacolp.document.websocket.protocol.DocumentWsAwarenessAction;
import com.jacolp.document.websocket.protocol.DocumentWsAwarenessMeta;
import com.jacolp.document.websocket.protocol.DocumentWsBinaryFrame;
import com.jacolp.document.websocket.protocol.DocumentWsCodec;
import com.jacolp.document.websocket.protocol.DocumentWsControlMessage;
import com.jacolp.document.websocket.protocol.DocumentWsControlType;
import com.jacolp.document.websocket.protocol.DocumentWsFrameType;
import com.jacolp.document.websocket.protocol.DocumentLinkCodec;
import com.jacolp.document.websocket.protocol.DocumentLinkPayload;
import com.jacolp.document.websocket.protocol.DocumentWsProtocolException;
import com.jacolp.document.websocket.protocol.DocumentWsLinkAcceptedMessage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 已认证的文档协作端点；Java 只路由不透明的 Yjs 字节，绝不解析正文。 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentWebSocketHandler extends AbstractWebSocketHandler {

    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Logger log = LoggerFactory.getLogger(DocumentWebSocketHandler.class);

    private final DocumentWsCodec codec;
    private final DocumentMapper documentMapper;
    private final DocumentAccessService accessService;
    private final DocumentRedisRepository documentRedisRepository;
    private final DocumentRoomManager roomManager;
    private final DocumentBootstrapService bootstrapService;
    private final DocumentSchedulePublisher schedulePublisher;
    private final DocumentSessionPresenceRegistry presenceRegistry;
    private final DocumentRoomLifecycleService lifecycleService;
    private final DocumentProperties properties;
    private final DocumentMetrics metrics;
    private final ConcurrentHashMap<String, Long> joinedDocumentIds = new ConcurrentHashMap<>();

    /** 创建不记录指标的文档 WebSocket 处理器，保留生产处理流程。 */
    public DocumentWebSocketHandler(DocumentWsCodec codec, DocumentMapper documentMapper,
                                    DocumentAccessService accessService,
                                    DocumentRedisRepository documentRedisRepository, DocumentRoomManager roomManager,
                                    DocumentBootstrapService bootstrapService, DocumentSchedulePublisher schedulePublisher,
                                    DocumentSessionPresenceRegistry presenceRegistry,
                                    DocumentRoomLifecycleService lifecycleService, DocumentProperties properties) {
        this(codec, documentMapper, accessService, documentRedisRepository, roomManager, bootstrapService, schedulePublisher,
                presenceRegistry, lifecycleService, properties, DocumentMetrics.noop());
    }

    /** 创建带运行指标的文档 WebSocket 处理器。 */
    @Autowired
    public DocumentWebSocketHandler(DocumentWsCodec codec, DocumentMapper documentMapper,
                                    DocumentAccessService accessService,
                                    DocumentRedisRepository documentRedisRepository, DocumentRoomManager roomManager,
                                    DocumentBootstrapService bootstrapService, DocumentSchedulePublisher schedulePublisher,
                                    DocumentSessionPresenceRegistry presenceRegistry,
                                    DocumentRoomLifecycleService lifecycleService, DocumentProperties properties,
                                    DocumentMetrics metrics) {
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper must not be null");
        this.accessService = Objects.requireNonNull(accessService, "accessService must not be null");
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository, "documentRedisRepository must not be null");
        this.roomManager = Objects.requireNonNull(roomManager, "roomManager must not be null");
        this.bootstrapService = Objects.requireNonNull(bootstrapService, "bootstrapService must not be null");
        this.schedulePublisher = Objects.requireNonNull(schedulePublisher, "schedulePublisher must not be null");
        this.presenceRegistry = Objects.requireNonNull(presenceRegistry, "presenceRegistry must not be null");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /**
     * 解析控制帧并处理 JOIN、LEAVE、PING 及协议错误响应。
     *
     * <p>控制帧只负责会话生命周期和确认消息；正文更新必须走二进制帧，并在写入前重新
     * 校验当前文档 ACL。</p>
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        DocumentWsControlMessage control;
        try {
            control = codec.decodeControl(message);
        } catch (DocumentWsProtocolException exception) {
            // 控制帧尚未通过解析，无法可信地使用客户端 requestId，只能生成新的错误关联 ID。
            sendError(session, UUID.randomUUID(), protocolErrorCode(exception), exception.getMessage());
            return;
        }

        try {
            // 只有控制协议明确支持的操作才能改变 Room 或触发服务端响应，未知类型不能静默执行。
            switch (control.type()) {
                case JOIN_DOCUMENT -> handleJoin(session, control);
                case LEAVE_DOCUMENT -> leaveSession(session);
                case PING -> sendControl(session, new DocumentWsControlMessage(protocolVersion(), DocumentWsControlType.PONG,
                        control.requestId(), null, null, null, null, null));
                default -> sendError(session, control.requestId(), "DOCUMENT_PROTOCOL_ERROR",
                        "unsupported client control frame type: " + control.type());
            }
        } catch (DocumentRoomLimitExceededException exception) {
            // Room 容量是资源级保护，返回专用错误码让客户端稍后重试，而不是当作协议错误。
            sendError(session, control.requestId(), "DOCUMENT_ROOM_LIMIT_EXCEEDED", exception.getMessage());
        } catch (DocumentAwarenessException exception) {
            // Awareness 身份是 JOIN 的必要条件或 Room 绑定约束，向客户端返回稳定原因而不是泛化为协议错误。
            sendError(session, control.requestId(), exception.code(), exception.getMessage());
        } catch (DocumentAccessDeniedException exception) {
            if (control.type() == DocumentWsControlType.JOIN_DOCUMENT
                    && joinedDocumentIds.containsKey(session.getId())) {
                // 重复 JOIN 发现授权已撤销时释放旧会话，避免连接继续占用 Room presence。
                leaveSession(session);
            }
            // 对外只区分 NOT_FOUND/FORBIDDEN 两种协议码，消息本身保持中性以避免资源枚举。
            String code = exception.reason() == DocumentAccessDeniedException.Reason.NOT_FOUND
                    ? "DOCUMENT_NOT_FOUND" : "DOCUMENT_FORBIDDEN";
            sendError(session, control.requestId(), code, exception.getMessage());
        } catch (DocumentRoomAccessException exception) {
            // Room/session 状态错误不会泄露底层异常，统一映射为文档不可用。
            sendError(session, control.requestId(), "DOCUMENT_FORBIDDEN", exception.getMessage());
        } catch (DocumentBootstrapException exception) {
            // bootstrap 失败后清理半初始化会话，避免客户端继续使用未同步的 Room。
            leaveSession(session);
            sendError(session, control.requestId(), "DOCUMENT_SYNC_FAILED", exception.getMessage());
        } catch (RuntimeException exception) {
            // 未预期异常不回传内部细节，只返回稳定的协议错误码。
            sendError(session, control.requestId(), "DOCUMENT_PROTOCOL_ERROR", "document request could not be completed");
        }
    }

    /**
     * 只接受客户端 CLIENT_UPDATE、LINK 和 AWARENESS 二进制帧，其他类型直接拒绝。
     *
     * <p>CLIENT_UPDATE/LINK 会写入 Redis 并进入异步刷盘；AWARENESS 仅在当前 Room 内转发，
     * 不产生持久化副作用。</p>
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        DocumentWsBinaryFrame frame;
        try {
            frame = codec.decodeBinary(message);
        } catch (DocumentWsProtocolException exception) {
            metrics.recordUpdateRejected();
            sendError(session, UUID.randomUUID(), protocolErrorCode(exception), exception.getMessage());
            return;
        }

        try {
            // CLIENT_UPDATE/LINK 进入持久化链路，AWARENESS 只做实时广播；快照/历史帧只能由服务端发送。
            if (frame.type() == DocumentWsFrameType.CLIENT_UPDATE) {
                acceptClientUpdate(session, frame);
            } else if (frame.type() == DocumentWsFrameType.LINK) {
                acceptLinkUpdate(session, frame);
            } else if (frame.type() == DocumentWsFrameType.AWARENESS) {
                broadcastAwareness(session, frame);
            } else {
                metrics.recordUpdateRejected();
                sendError(session, frame.eventId(), "DOCUMENT_PROTOCOL_ERROR", "binary frame type is not accepted from clients");
            }
        } catch (DocumentWsProtocolException exception) {
            // LINK 的固定 Envelope 校验失败时不能落入通用更新失败，否则客户端无法区分协议错误。
            metrics.recordUpdateRejected();
            sendError(session, frame.eventId(), protocolErrorCode(exception), exception.getMessage());
        } catch (DocumentRoomAccessException exception) {
            // 未 JOIN、未完成 bootstrap 或无全局写 scope 均属于资源访问失败。
            metrics.recordUpdateRejected();
            sendError(session, frame.eventId(), "DOCUMENT_FORBIDDEN", exception.getMessage());
        } catch (DocumentAccessDeniedException exception) {
            // 最新 ACL 在更新前复核失败时，不发送 ACK/广播，防止客户端误以为写入成功。
            metrics.recordUpdateRejected();
            sendError(session, frame.eventId(), "DOCUMENT_FORBIDDEN", exception.getMessage());
        } catch (RuntimeException exception) {
            // 其余写链路异常统一返回失败码，详细原因留在服务端日志或恢复链路中。
            metrics.recordUpdateRejected();
            sendError(session, frame.eventId(), "DOCUMENT_UPDATE_ACCEPT_FAILED", "document update could not be accepted");
        }
    }

    /** 连接关闭后释放本机会话、presence 和延迟关闭状态。 */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        leaveSession(session);
    }

    /** 传输异常与正常关闭使用同一套会话清理流程。 */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        leaveSession(session);
    }

    /** 校验文档 ACL 后建立 Room 归属，按顺序发送 bootstrap 并在完成后激活会话。 */
    private void handleJoin(WebSocketSession session, DocumentWsControlMessage control) {
        long awarenessClientId = requireAwarenessClientId(control.awarenessClientId());
        long documentId = requireDocumentId(control.documentId());
        CurrentPrincipal principal = DocumentWebSocketHandshakeInterceptor.requirePrincipal(session.getAttributes());
        DocumentAccess access = accessService.requireRead(documentId, principal.userId());
        Long alreadyJoinedDocumentId = joinedDocumentIds.get(session.getId());
        // 一个 WebSocket 会话只能绑定一个文档；切换文档必须先断开旧会话，避免状态串写。
        if (alreadyJoinedDocumentId != null) {
            if (alreadyJoinedDocumentId != documentId) {
                throw new DocumentRoomAccessException("a WebSocket session may join only one document at a time");
            }
            DocumentRoom room = roomManager.find(documentId)
                    .orElseThrow(() -> new DocumentRoomAccessException("document Room is not available"));
            // 同一会话重复 JOIN 时不重放文档，但刷新会话权限以反映最新 ACL。
            room.join(session, principal, access, awarenessClientId);
            if (!sendControl(session, new DocumentWsControlMessage(protocolVersion(), DocumentWsControlType.JOIN_ACCEPTED,
                    control.requestId(), documentId, null, null, null, null))) {
                return;
            }
            sendControl(session, new DocumentWsControlMessage(protocolVersion(), DocumentWsControlType.SYNC_COMPLETE,
                    control.requestId(), documentId, null, null, null, null));
            return;
        }

        DocumentDO document = access.document();
        DocumentRoom room = roomManager.getOrCreate(documentId, requireOwnerUserId(document));
        // 先登记本地 Room 和 presence，再发送 bootstrap，确保期间 CLOSE 不会误判无人在线。
        DocumentSessionContext context = room.join(session, principal, access, awarenessClientId);
        joinedDocumentIds.put(session.getId(), documentId);
        roomManager.refreshRuntimeMetrics();
        try {
            presenceRegistry.register(documentId, session.getId());
            lifecycleService.reopen(document, principal.userId());
            if (!sendControl(session, new DocumentWsControlMessage(protocolVersion(), DocumentWsControlType.JOIN_ACCEPTED,
                    control.requestId(), documentId, null, null, null, null))) {
                return;
            }
            if (!publishAwarenessJoin(room, context)) {
                return;
            }
            // 在快照、持久化日志和 Redis 待写入更新全部发送完前，会话不会进入 active；
            // 此期间收到的二进制编辑会被拒绝。
            bootstrapService.sendBootstrap(documentId, context.session());
            room.markActive(session.getId());
            sendControl(session, new DocumentWsControlMessage(protocolVersion(), DocumentWsControlType.SYNC_COMPLETE,
                    control.requestId(), documentId, null, null, null, null));
        } catch (RuntimeException exception) {
            // bootstrap 任一步骤失败都不能留下半初始化会话，否则后续更新会绕过完整恢复流程。
            leaveSession(session);
            throw exception;
        }
    }

    /** 校验客户端更新、先写 Redis，再确认/广播并安排异步刷盘。 */
    private void acceptClientUpdate(WebSocketSession session, DocumentWsBinaryFrame frame) {
        DocumentRoom room = requireActiveRoom(session);
        DocumentSessionContext context = room.requireSession(session.getId());
        if (rejectInvalidClientPayload(session, frame, "Yjs update")) return;

        CurrentPrincipal principal = DocumentWebSocketHandshakeInterceptor.requirePrincipal(session.getAttributes());
        if (context.userId() != principal.userId() || !context.canWrite()) {
            throw new DocumentRoomAccessException("document session does not have write permission");
        }
        // WebSocket 握手只要求读或写 scope；真正提交更新时仍必须具备全局写 scope，不能由文档 ACL 单独提升能力。
        if (!PermissionScopeMatcher.grants(principal.scopes(), "document:write")) {
            throw new DocumentRoomAccessException("document session does not have document:write scope");
        }
        // SessionContext 是快速能力判断；每次真正写入前仍以数据库中的最新 ACL 为准。
        DocumentAccess access = accessService.requireWrite(room.documentId(), principal.userId());
        context.updateAccess(access);
        long now = System.currentTimeMillis();
        String redisOpId = documentRedisRepository.appendPendingUpdate(new DocumentPendingUpdate(room.documentId(),
                frame.payload(), frame.eventId().toString(), principal.userId(), principal.clientId(), now));

        // Redis Stream 写入成功即代表服务端已接收；后续 MySQL 审计更新失败仍由恢复链路继续处理。
        // 先将不透明更新写入 Redis，随后才确认和广播；之后的 HTTP 或 WebSocket 发送失败时，
        // 已接收的编辑仍可被恢复。
        int modified = documentMapper.updateLastModificationIfActive(room.documentId(), principal.userId(),
                LocalDateTime.now(APPLICATION_ZONE), principal.userId());
        if (modified != 1) {
            // Redis 已经接收更新，但数据库 ACL/active 条件未命中；不发送 ACK，避免客户端误以为更新已完成。
            throw new DocumentRoomAccessException("document no longer accepts updates");
        }
        saveRoomMeta(room.documentId(), room.ownerUserId(), principal.userId(), now);
        sendControl(room.requireSession(session.getId()).session(), new DocumentWsControlMessage(protocolVersion(),
                DocumentWsControlType.UPDATE_ACCEPTED, frame.eventId(), room.documentId(), frame.eventId(), redisOpId, null, null));
        metrics.recordUpdateAccepted();
        List<DocumentSessionContext> removedSessions = room.broadcast(codec.encodeBinary(new DocumentWsBinaryFrame(
                DocumentWsFrameType.CRDT_UPDATE, frame.eventId(), frame.payload())), session.getId());
        cleanupRemovedSessions(room, removedSessions);
        scheduleFlushLog(room.documentId());
    }

    /** 校验 LINK Envelope，原子写入双 Stream，再发送专用 ACK 并广播 raw Yjs update。 */
    private void acceptLinkUpdate(WebSocketSession session, DocumentWsBinaryFrame frame) {
        if (rejectInvalidClientPayload(session, frame, "LINK payload")) return;
        DocumentLinkPayload link = DocumentLinkCodec.decode(frame.payload());
        DocumentRoom room = requireActiveRoom(session);
        DocumentSessionContext context = room.requireSession(session.getId());

        CurrentPrincipal principal = DocumentWebSocketHandshakeInterceptor.requirePrincipal(session.getAttributes());
        if (context.userId() != principal.userId() || !context.canWrite()) {
            throw new DocumentRoomAccessException("document session does not have write permission");
        }
        // LINK 仍然是文档内容变更，沿用普通 CLIENT_UPDATE 的全局写 scope 和最新 ACL 复核。
        if (!PermissionScopeMatcher.grants(principal.scopes(), "document:write")) {
            throw new DocumentRoomAccessException("document session does not have document:write scope");
        }
        DocumentAccess access = accessService.requireWrite(room.documentId(), principal.userId());
        context.updateAccess(access);
        long now = System.currentTimeMillis();
        DocumentLinkAcceptedRedisOperations operations = documentRedisRepository.appendLinkPendingUpdate(
                new DocumentPendingUpdate(room.documentId(), link.rawYjsUpdate(), frame.eventId().toString(),
                        principal.userId(), principal.clientId(), now),
                new DocumentPendingBinding(room.documentId(), link.envelope().encodeBody()));

        // Redis 双 Stream 原子入队后沿用普通更新的 active 条件，避免被撤销的文档继续确认新的变更。
        int modified = documentMapper.updateLastModificationIfActive(room.documentId(), principal.userId(),
                LocalDateTime.now(APPLICATION_ZONE), principal.userId());
        if (modified != 1) {
            throw new DocumentRoomAccessException("document no longer accepts LINK updates");
        }
        saveRoomMeta(room.documentId(), room.ownerUserId(), principal.userId(), now);
        // 入队与运行时元数据都已成功，刷盘调度不能依赖 ACK 是否送达；否则只能等恢复扫描补偿。
        scheduleFlushLog(room.documentId());
        if (!sendLinkAccepted(room.requireSession(session.getId()).session(), room.documentId(), frame, operations)) {
            // LINK 的 ACK 是发送者确认点；ACK 发送失败时不向其他会话广播，避免出现无法确认的实时变更。
            return;
        }
        metrics.recordUpdateAccepted();
        List<DocumentSessionContext> removedSessions = room.broadcast(codec.encodeBinary(new DocumentWsBinaryFrame(
                DocumentWsFrameType.CRDT_UPDATE, frame.eventId(), link.rawYjsUpdate())), session.getId());
        cleanupRemovedSessions(room, removedSessions);
    }

    /** 先缓存发送者最新的 Awareness 帧，再广播给同一 Room 的其他会话，不进入持久化链路。 */
    private void broadcastAwareness(WebSocketSession session, DocumentWsBinaryFrame frame) {
        DocumentRoom room = requireActiveRoom(session);
        if (rejectInvalidClientPayload(session, frame, "Awareness payload")) return;
        // Room 在同一把锁内校验成员并替换缓存，避免关闭回调之后仍保存幽灵会话状态。
        room.rememberAwareness(session.getId(), frame);
        List<DocumentSessionContext> removedSessions = room.broadcast(codec.encodeBinary(frame), session.getId());
        cleanupRemovedSessions(room, removedSessions);
    }

    /** 统一限制客户端二进制 payload，避免无效数据进入持久化、缓存或广播链路。 */
    private boolean rejectInvalidClientPayload(WebSocketSession session, DocumentWsBinaryFrame frame,
                                               String payloadName) {
        int payloadBytes = frame.payload().length;
        if (payloadBytes > properties.getWebsocket().getMaxUpdateBytes()) {
            metrics.recordUpdateRejected();
            sendError(session, frame.eventId(), "DOCUMENT_UPDATE_TOO_LARGE",
                    payloadName + " exceeds configured maximum size");
            return true;
        }
        // 空 payload 没有任何可应用内容，却会污染 ACK、缓存和广播链路，因此直接拒绝。
        if (payloadBytes == 0) {
            metrics.recordUpdateRejected();
            sendError(session, frame.eventId(), "DOCUMENT_PROTOCOL_ERROR", payloadName + " must not be empty");
            return true;
        }
        return false;
    }

    /** 获取已 JOIN 且已完成 bootstrap 的本机会话 Room。 */
    private DocumentRoom requireActiveRoom(WebSocketSession session) {
        Long documentId = joinedDocumentIds.get(session.getId());
        if (documentId == null) {
            // 未记录 JOIN 关系的会话不能提交任何二进制数据，即使握手本身已经通过认证。
            throw new DocumentRoomAccessException("WebSocket session has not joined a document");
        }
        DocumentRoom room = roomManager.find(documentId)
                .orElseThrow(() -> new DocumentRoomAccessException("document Room is not available"));
        if (room.requireSession(session.getId()).syncStatus() != DocumentSessionSyncStatus.ACTIVE) {
            // bootstrap 尚未完整发送时，客户端状态可能落后于服务端；必须等 SYNC_COMPLETE 后才接受编辑。
            throw new DocumentRoomAccessException("document session is still synchronizing");
        }
        return room;
    }

    /** 根据数据库审计时间刷新 Redis Room Meta，并生成新的 reopen 令牌。 */
    private void updateRoomMeta(DocumentDO document, long userId) {
        long lastModifiedAt = document.getLastModifyTime() == null
                ? System.currentTimeMillis()
                : document.getLastModifyTime().atZone(APPLICATION_ZONE).toInstant().toEpochMilli();
        saveRoomMeta(document.getId(), requireOwnerUserId(document), userId, lastModifiedAt);
    }

    /** 以文档所有者保存 Room Meta，并拒绝 Redis 中的文档归属冲突。 */
    private void saveRoomMeta(long documentId, long ownerUserId, long lastModifyUserId, long lastModifiedAt) {
        Optional<DocumentRoomMeta> existing = documentRedisRepository.findRoomMeta(documentId);
        if (existing.isPresent() && existing.get().ownerUserId() != ownerUserId) {
            // Redis 中已有的 Room owner 与数据库不一致时拒绝覆盖，防止错误数据被重新绑定。
            throw new DocumentRoomAccessException("Redis room meta does not match document owner");
        }
        documentRedisRepository.saveRoomMeta(new DocumentRoomMeta(documentId, ownerUserId, false,
                UUID.randomUUID().toString(), lastModifiedAt, lastModifyUserId));
    }

    /** 幂等移除会话并在本机最后离开时请求延迟 CLOSE。 */
    private void leaveSession(WebSocketSession session) {
        String sessionId = session.getId();
        Long documentId = joinedDocumentIds.get(sessionId);
        if (documentId == null) {
            // 未完成 JOIN 的连接也会触发关闭回调；没有 Room 归属时此前不会成功登记 presence，直接返回即可。
            return;
        }
        DocumentRoom room = roomManager.find(documentId).orElse(null);
        if (room == null) {
            // Room 可能已被最终关闭流程移除，不能让已关闭连接继续占用本地归属表。
            joinedDocumentIds.remove(sessionId, documentId);
            presenceRegistry.unregister(sessionId);
            return;
        }
        Optional<DocumentSessionContext> removed = room.removeSession(sessionId);
        if (removed.isPresent()) {
            cleanupRemovedSessions(room, List.of(removed.get()));
        } else {
            // 发送失败清理和关闭回调可能并发到达；只有实际移除者负责广播 REMOVE。
            joinedDocumentIds.remove(sessionId, documentId);
            presenceRegistry.unregister(sessionId);
            roomManager.refreshRuntimeMetrics();
        }
    }

    /** 将已有会话的元数据和最新 Awareness 帧回放给新会话，再广播新会话的 UPSERT。 */
    private boolean publishAwarenessJoin(DocumentRoom room, DocumentSessionContext joined) {
        for (DocumentRoom.AwarenessSnapshot snapshot : room.awarenessSnapshots()) {
            DocumentSessionContext existing = snapshot.context();
            if (existing.sessionId().equals(joined.sessionId())) {
                continue;
            }
            if (!containsSession(room, existing.sessionId())) {
                // 快照建立后旧会话可能已经退出；其 REMOVE 会由统一清理流程发送，不能回放过期状态。
                continue;
            }
            if (!sendAwarenessMeta(joined.session(), awarenessUpsert(room.documentId(), existing))) {
                return false;
            }
            if (snapshot.latestFrame() != null && containsSession(room, existing.sessionId())
                    && !sendAwarenessFrame(joined.session(), snapshot.latestFrame())) {
                return false;
            }
        }
        if (!containsSession(room, joined.sessionId())) {
            // 回放过程中连接可能已经关闭；不能在其离开后再次广播 UPSERT。
            return false;
        }
        List<DocumentSessionContext> removedSessions = room.broadcast(
                codec.encodeAwarenessMeta(awarenessUpsert(room.documentId(), joined)), joined.sessionId());
        cleanupRemovedSessions(room, removedSessions);
        return containsSession(room, joined.sessionId());
    }

    /** 将已有会话最新的 Awareness 二进制帧回放给新会话；发送失败时走统一退出清理。 */
    private boolean sendAwarenessFrame(WebSocketSession session, DocumentWsBinaryFrame awarenessFrame) {
        try {
            session.sendMessage(codec.encodeBinary(awarenessFrame));
            return true;
        } catch (IOException | RuntimeException exception) {
            // 回放发送失败代表目标连接不可继续使用，必须释放其 Room、颜色和 presence 状态。
            leaveSession(session);
            return false;
        }
    }

    /** 将 Awareness 元数据事件发送给单个会话；失败时进入统一退出清理。 */
    private boolean sendAwarenessMeta(WebSocketSession session, DocumentWsAwarenessMeta metadata) {
        try {
            session.sendMessage(codec.encodeAwarenessMeta(metadata));
            return true;
        } catch (IOException | RuntimeException exception) {
            // 元数据发送失败与普通控制帧失败一样，必须释放颜色、presence 和远端元数据。
            leaveSession(session);
            return false;
        }
    }

    /** 编码并发送控制帧；发送失败时释放对应会话。 */
    private boolean sendControl(WebSocketSession session, DocumentWsControlMessage control) {
        try {
            session.sendMessage(codec.encodeControl(control));
            return true;
        } catch (IOException | RuntimeException exception) {
            // 底层会话可能以 IOException 或已关闭/超限状态抛出运行时异常，两者都必须释放 Room 颜色。
            leaveSession(session);
            return false;
        }
    }

    /** 发送 LINK 专用确认；发送失败只清理会话，不回滚已经入队的 Redis 数据。 */
    private boolean sendLinkAccepted(WebSocketSession session, long documentId, DocumentWsBinaryFrame frame,
                                     DocumentLinkAcceptedRedisOperations operations) {
        try {
            session.sendMessage(codec.encodeLinkAccepted(new DocumentWsLinkAcceptedMessage(protocolVersion(),
                    DocumentWsControlType.LINK_ACCEPTED, frame.eventId(), documentId, frame.eventId(),
                    operations.updatesRedisOpId(), operations.bindingRedisOpId(), "QUEUED")));
            return true;
        } catch (IOException | RuntimeException exception) {
            // 双 Stream 已经成功写入，ACK 失败时保留 At-Least-Once 数据并释放不可用会话。
            leaveSession(session);
            return false;
        }
    }

    /** 处理 Room 广播中因发送失败被移除的所有会话，直到没有级联清理事件。 */
    private void cleanupRemovedSessions(DocumentRoom room, Collection<DocumentSessionContext> removedSessions) {
        if (removedSessions == null || removedSessions.isEmpty()) {
            return;
        }
        ArrayDeque<DocumentSessionContext> pending = new ArrayDeque<>(removedSessions);
        Set<String> cleanedSessionIds = new HashSet<>();
        while (!pending.isEmpty()) {
            DocumentSessionContext removed = pending.removeFirst();
            if (!cleanedSessionIds.add(removed.sessionId())) {
                // 同一个会话可能同时触发发送失败和关闭回调，REMOVE 只允许广播一次。
                continue;
            }
            joinedDocumentIds.remove(removed.sessionId(), room.documentId());
            presenceRegistry.unregister(removed.sessionId());
            DocumentWsAwarenessMeta remove = awarenessRemove(room.documentId(), removed);
            pending.addAll(room.broadcast(codec.encodeAwarenessMeta(remove), removed.sessionId()));
        }
        if (room.sessionCount() == 0) {
            try {
                lifecycleService.requestClose(room.documentId(), room.ownerUserId());
            } catch (RuntimeException exception) {
                log.warn("Could not request delayed CLOSE for documentId={}: {}", room.documentId(), exception.getMessage());
            }
        }
        roomManager.refreshRuntimeMetrics();
    }

    /** 判断会话仍在指定 Room 内，避免连接已清理后继续发布 UPSERT。 */
    private static boolean containsSession(DocumentRoom room, String sessionId) {
        return room.sessions().stream().anyMatch(context -> context.sessionId().equals(sessionId));
    }

    /** 构造包含认证用户和服务端颜色的 Awareness UPSERT 元数据。 */
    private DocumentWsAwarenessMeta awarenessUpsert(long documentId, DocumentSessionContext context) {
        return new DocumentWsAwarenessMeta(protocolVersion(), DocumentWsControlType.AWARENESS_META,
                UUID.randomUUID(), DocumentWsAwarenessAction.UPSERT, documentId, context.awarenessClientId(),
                context.sessionId(), context.userId(), context.username(), context.cursorColor());
    }

    /** 构造退出会话所需的最小 Awareness REMOVE 元数据。 */
    private DocumentWsAwarenessMeta awarenessRemove(long documentId, DocumentSessionContext context) {
        return new DocumentWsAwarenessMeta(protocolVersion(), DocumentWsControlType.AWARENESS_META,
                UUID.randomUUID(), DocumentWsAwarenessAction.REMOVE, documentId, context.awarenessClientId(),
                context.sessionId(), null, null, null);
    }

    /** 发布 FLUSH_LOG 信号；Rabbit 失败不回滚已写入 Redis 的更新。 */
    private void scheduleFlushLog(long documentId) {
        try {
            schedulePublisher.scheduleFlushLog(documentId);
        } catch (RuntimeException exception) {
            // Redis Stream 追加已保存这条更新；恢复扫描会在之后为同一文档重新发布 FLUSH_LOG 信号。
            log.warn("Could not schedule FLUSH_LOG for documentId={}: {}", documentId, exception.getMessage());
        }
    }

    /** 发送统一格式的错误控制帧，并为缺失 requestId 生成关联 ID。 */
    private void sendError(WebSocketSession session, UUID requestId, String code, String message) {
        // 某些错误发生在解析 requestId 之前，因此统一补齐 ID 让客户端仍能关联错误响应。
        sendControl(session, new DocumentWsControlMessage(protocolVersion(), DocumentWsControlType.ERROR,
                requestId == null ? UUID.randomUUID() : requestId, null, null, null, code, message));
    }

    /** 返回当前配置的 WebSocket 协议版本。 */
    private int protocolVersion() {
        return properties.getWebsocket().getProtocolVersion();
    }

    /** 校验控制帧中的文档 ID 为正数。 */
    private static long requireDocumentId(Long documentId) {
        if (documentId == null || documentId <= 0) {
            // 文档 ID 既是 Room/Redis key 的组成部分，也是数据库查询范围，空值和非正数都不能继续传播。
            throw new DocumentRoomAccessException("documentId must be positive");
        }
        return documentId;
    }

    /** 校验 JOIN 控制帧中的 Awareness client ID 为必填正数。 */
    private static long requireAwarenessClientId(Long awarenessClientId) {
        if (awarenessClientId == null) {
            throw DocumentAwarenessException.required();
        }
        if (awarenessClientId <= 0) {
            throw DocumentAwarenessException.invalid();
        }
        return awarenessClientId;
    }

    /** 校验并返回文档所有者 ID，避免损坏的持久化数据进入 Room key/生命周期状态。 */
    private static long requireOwnerUserId(DocumentDO document) {
        if (document.getOwnerUserId() == null || document.getOwnerUserId() <= 0) {
            throw new DocumentRoomAccessException("document owner is invalid");
        }
        return document.getOwnerUserId();
    }

    /** 把协议版本错误映射为客户端可识别的错误码。 */
    private static String protocolErrorCode(DocumentWsProtocolException exception) {
        // 版本错误需要让客户端明确知道应升级协议，其余解析失败统一归入协议错误。
        return exception.getMessage().contains("protocol version")
                ? "DOCUMENT_PROTOCOL_VERSION_UNSUPPORTED" : "DOCUMENT_PROTOCOL_ERROR";
    }
}
