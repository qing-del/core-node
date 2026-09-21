package com.jacolp.document.websocket;

import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentOpLogDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentOpLogMapper;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.StoredDocumentPendingUpdate;
import com.jacolp.document.websocket.exception.DocumentBootstrapException;
import com.jacolp.document.websocket.protocol.DocumentWsBinaryFrame;
import com.jacolp.document.websocket.protocol.DocumentWsCodec;
import com.jacolp.document.websocket.protocol.DocumentWsFrameType;
import com.jacolp.framework.minio.MinioBucketResolver;
import com.jacolp.framework.minio.MinioObjectStorage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketSession;

/** 先读取 Redis pending，再读取历史来源并按快照、持久化日志、Redis 的顺序发送 bootstrap。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentBootstrapService {

    private static final UUID BOOTSTRAP_EVENT_ID = new UUID(0L, 0L);

    private final MinioObjectStorage minioObjectStorage;
    private final MinioBucketResolver minioBucketResolver;
    private final DocumentMapper documentMapper;
    private final DocumentOpLogMapper documentOpLogMapper;
    private final DocumentRedisRepository documentRedisRepository;
    private final DocumentWsCodec codec;
    private final DocumentProperties properties;
    private final TransactionTemplate bootstrapTransactionTemplate;

    /** 创建不解析 Yjs 内容、只负责收集和发送 Bootstrap 二进制帧的服务。 */
    public DocumentBootstrapService(MinioObjectStorage minioObjectStorage, MinioBucketResolver minioBucketResolver,
                                    DocumentMapper documentMapper, DocumentOpLogMapper documentOpLogMapper,
                                    DocumentRedisRepository documentRedisRepository,
                                    PlatformTransactionManager transactionManager,
                                    DocumentWsCodec codec, DocumentProperties properties) {
        this.minioObjectStorage = Objects.requireNonNull(minioObjectStorage, "minioObjectStorage must not be null");
        this.minioBucketResolver = Objects.requireNonNull(minioBucketResolver, "minioBucketResolver must not be null");
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper must not be null");
        this.documentOpLogMapper = Objects.requireNonNull(documentOpLogMapper, "documentOpLogMapper must not be null");
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository, "documentRedisRepository must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.bootstrapTransactionTemplate = new TransactionTemplate(transactionManager);
        this.bootstrapTransactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.bootstrapTransactionTemplate.setReadOnly(true);
    }

    /** 向新会话发送可恢复的全部内容，并把底层读取/传输错误统一包装。 */
    public void sendBootstrap(long documentId, WebSocketSession session) {
        Objects.requireNonNull(session, "session must not be null");
        requirePositive(documentId, "documentId");
        try {
            // 先固定 Redis 读取边界；此调用之后产生的 Update 由已加入 Room 的实时转发覆盖。
            List<StoredDocumentPendingUpdate> pendingUpdates = readPendingUpdates(documentId);
            BootstrapHistory history = readBootstrapHistory(documentId);
            // RR 事务已经结束，MinIO 和 WebSocket 网络 IO 不会延长数据库 ReadView 生命周期。
            sendSnapshotIfPresent(history.contentObjectKey(), session);
            sendDurableUpdates(history.durableUpdates(), session);
            sendPendingUpdates(pendingUpdates, session);
        } catch (IOException exception) {
            throw new DocumentBootstrapException("could not send document bootstrap", exception);
        } catch (DocumentBootstrapException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DocumentBootstrapException("could not read document bootstrap", exception);
        }
    }

    /** 在数据库读取结束后读取不可变快照，并发送快照状态作为客户端基线。 */
    private void sendSnapshotIfPresent(String objectKey, WebSocketSession session) throws Exception {
        if (objectKey == null || objectKey.isBlank()) {
            // 新文档可能尚未产生 Snapshot，客户端此时以空 Y.Doc 作为恢复基线。
            return;
        }
        String bucket = minioBucketResolver.requireBucket("document");
        byte[] snapshot = minioObjectStorage.read(bucket, objectKey, properties.getSnapshot().getMaxBytes());
        send(session, DocumentWsFrameType.SNAPSHOT_STATE, BOOTSTRAP_EVENT_ID, snapshot);
    }

    /** 在同一个 RR ReadView 中读取快照位点之后的全部 MySQL 持久化更新。 */
    private BootstrapHistory readBootstrapHistory(long documentId) {
        BootstrapHistory history = bootstrapTransactionTemplate.execute(status -> {
            // 这是本事务中的第一次一致性读取，负责建立本轮 Bootstrap 的 MySQL ReadView。
            DocumentDO document = documentMapper.selectActiveById(documentId);
            if (document == null) {
                throw new DocumentBootstrapException("document does not exist or is not accessible");
            }
            return new BootstrapHistory(document.getContentObjectKey(),
                    readDurableUpdates(documentId, document.getPersistedLogId()));
        });
        if (history == null) {
            throw new DocumentBootstrapException("bootstrap history transaction returned no data");
        }
        return history;
    }

    /** 在当前事务的同一个 ReadView 中分页收集快照位点之后的 MySQL 更新。 */
    private List<byte[]> readDurableUpdates(long documentId, Long persistedLogId) {
        long afterId = persistedLogId == null ? 0L : persistedLogId;
        int batchSize = Math.max(1, properties.getFlushLog().getBatchSize());
        List<byte[]> updatesToSend = new ArrayList<>();
        while (true) {
            List<DocumentOpLogDO> updates = documentOpLogMapper.selectByDocumentIdAfterId(documentId, afterId, batchSize);
            if (updates == null || updates.isEmpty()) {
                // 当前快照位点之后没有 MySQL 日志时，说明 durable bootstrap 已经发送完毕。
                return List.copyOf(updatesToSend);
            }
            for (DocumentOpLogDO update : updates) {
                if (update.getId() == null || update.getId() <= afterId || update.getUpdateData() == null) {
                    // 位点必须严格递增且带有二进制内容，否则客户端无法按顺序重建一致状态。
                    throw new DocumentBootstrapException("document op log contains an invalid bootstrap update");
                }
                // 复制二进制内容，使事务结束后发送阶段不依赖 MyBatis 返回对象的生命周期。
                updatesToSend.add(update.getUpdateData().clone());
                afterId = update.getId();
            }
            if (updates.size() < batchSize) {
                // 返回不足一整批意味着查询范围已经到达当前日志尾部，无需继续轮询数据库。
                return List.copyOf(updatesToSend);
            }
        }
    }

    /** 发送已从同一个 RR ReadView 完整收集的持久化更新。 */
    private void sendDurableUpdates(List<byte[]> updates, WebSocketSession session) throws IOException {
        for (byte[] update : updates) {
            send(session, DocumentWsFrameType.BOOTSTRAP_UPDATE, BOOTSTRAP_EVENT_ID, update);
        }
    }

    /** 读取所有仍在 Redis Stream 中可见的更新，固定 Redis 初始读取边界。 */
    private List<StoredDocumentPendingUpdate> readPendingUpdates(long documentId) {
        // 这里刻意读取所有可见 Stream 条目：重连时不能因为 FLUSH_LOG 尚未消费下一批，
        // 就遗漏一条已经接收成功的更新。
        return documentRedisRepository.readPendingUpdates(documentId, Integer.MAX_VALUE);
    }

    /** 发送 Redis 初始读取中捕获的更新，重复覆盖交给客户端 Yjs 合并。 */
    private void sendPendingUpdates(List<StoredDocumentPendingUpdate> updates, WebSocketSession session)
            throws IOException {
        for (StoredDocumentPendingUpdate update : updates) {
            send(session, DocumentWsFrameType.BOOTSTRAP_UPDATE, BOOTSTRAP_EVENT_ID, update.update().updateData());
        }
    }

    /** 使用统一 codec 发送一个带明确帧类型和事件 ID 的二进制帧。 */
    private void send(WebSocketSession session, DocumentWsFrameType type, UUID eventId, byte[] yjsBytes) throws IOException {
        session.sendMessage(codec.encodeBinary(new DocumentWsBinaryFrame(type, eventId, yjsBytes)));
    }

    /** 校验 Bootstrap 标识是正数。 */
    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    /** RR 事务结束后可安全交给 MinIO 和 WebSocket 阶段处理的 Bootstrap 历史数据。 */
    private record BootstrapHistory(String contentObjectKey, List<byte[]> durableUpdates) {
    }
}
