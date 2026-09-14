package com.jacolp.document.application.migration;

import com.jacolp.document.application.compact.DocumentSnapshotStorage;
import com.jacolp.document.application.yjs.YjsMergeClient;
import com.jacolp.document.application.yjs.YjsNodeIdentityMigrationResult;
import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentOpLogDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentOpLogMapper;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.StoredDocumentPendingUpdate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 编排历史 Yjs 快照、持久化日志和 Redis pending 的节点身份迁移。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentNodeIdentityMigrationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentNodeIdentityMigrationService.class);

    private final DocumentMapper documentMapper;
    private final DocumentOpLogMapper documentOpLogMapper;
    private final DocumentSnapshotStorage snapshotStorage;
    private final DocumentRedisRepository redisRepository;
    private final YjsMergeClient yjsMergeClient;
    private final DocumentProperties properties;

    /** 保存迁移任务需要的持久化、Redis、对象存储和 Yjs 适配器。 */
    public DocumentNodeIdentityMigrationService(DocumentMapper documentMapper,
                                                 DocumentOpLogMapper documentOpLogMapper,
                                                 DocumentSnapshotStorage snapshotStorage,
                                                 DocumentRedisRepository redisRepository,
                                                 YjsMergeClient yjsMergeClient,
                                                 DocumentProperties properties) {
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper must not be null");
        this.documentOpLogMapper = Objects.requireNonNull(documentOpLogMapper, "documentOpLogMapper must not be null");
        this.snapshotStorage = Objects.requireNonNull(snapshotStorage, "snapshotStorage must not be null");
        this.redisRepository = Objects.requireNonNull(redisRepository, "redisRepository must not be null");
        this.yjsMergeClient = Objects.requireNonNull(yjsMergeClient, "yjsMergeClient must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /** 按主键游标扫描全部文档，单个文档失败不阻断其他文档迁移。 */
    public DocumentNodeIdentityMigrationSummary migrateAll() {
        int pageSize = requirePositive(properties.getNodeMigration().getPageSize(), "node migration pageSize");
        long afterId = 0L;
        int scanned = 0;
        int migrated = 0;
        int skipped = 0;
        int failures = 0;

        while (true) {
            List<DocumentDO> documents = documentMapper.selectByIdAfter(afterId, pageSize);
            if (documents == null || documents.isEmpty()) {
                break;
            }
            for (DocumentDO document : documents) {
                if (document == null || document.getId() == null || document.getId() <= afterId) {
                    throw new DocumentNodeIdentityMigrationException("document ID cursor is not strictly increasing");
                }
                afterId = document.getId();
                scanned += 1;
                try {
                    DocumentNodeIdentityMigrationOutcome outcome = migrateDocument(document.getId());
                    if (outcome.status() == DocumentNodeIdentityMigrationOutcome.Status.MIGRATED) {
                        migrated += 1;
                    } else {
                        skipped += 1;
                    }
                } catch (RuntimeException exception) {
                    failures += 1;
                    log.error("document node identity migration failed documentId={} reason={}", document.getId(),
                            exception.getMessage(), exception);
                }
            }
        }

        DocumentNodeIdentityMigrationSummary summary = new DocumentNodeIdentityMigrationSummary(scanned, migrated,
                skipped, failures);
        log.info("document node identity migration completed scanned={} migrated={} skipped={} failures={}",
                summary.scanned(), summary.migrated(), summary.skipped(), summary.failures());
        return summary;
    }

    /** 迁移单个文档；CAS 竞争时从最新快照重新读取并重试。 */
    public DocumentNodeIdentityMigrationOutcome migrateDocument(long documentId) {
        if (documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
        int maxAttempts = requirePositive(properties.getNodeMigration().getMaxCasRetries(),
                "node migration maxCasRetries");
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            DocumentDO document = documentMapper.selectById(documentId);
            if (document == null) {
                return new DocumentNodeIdentityMigrationOutcome(documentId,
                        DocumentNodeIdentityMigrationOutcome.Status.NO_CHANGES, 0L, null);
            }
            if (redisRepository.countPresence(documentId) > 0) {
                log.info("document node identity migration skipped active documentId={} attempt={}", documentId,
                        attempt);
                return new DocumentNodeIdentityMigrationOutcome(documentId,
                        DocumentNodeIdentityMigrationOutcome.Status.SKIPPED_ACTIVE_SESSIONS,
                        persistedLogId(document), null);
            }

            MigrationState state = mergeLatestState(document);
            if (!state.changed()) {
                return new DocumentNodeIdentityMigrationOutcome(documentId,
                        DocumentNodeIdentityMigrationOutcome.Status.NO_CHANGES, state.cutoffLogId(), null);
            }

            String objectKey = snapshotStorage.write(documentId, state.state());
            int affected = documentMapper.updateSnapshotPointerIfPersistedLogId(documentId,
                    persistedLogId(document), objectKey, state.cutoffLogId());
            if (affected == 1) {
                cleanupCoveredLogs(documentId, state.cutoffLogId());
                return new DocumentNodeIdentityMigrationOutcome(documentId,
                        DocumentNodeIdentityMigrationOutcome.Status.MIGRATED, state.cutoffLogId(), objectKey);
            }

            discardUnreferencedSnapshot(documentId, objectKey);

            log.info("document node identity migration lost snapshot CAS documentId={} attempt={}/{}", documentId,
                    attempt, maxAttempts);
        }
        return new DocumentNodeIdentityMigrationOutcome(documentId,
                DocumentNodeIdentityMigrationOutcome.Status.CAS_LOST, 0L, null);
    }

    /** 合并当前快照、全部持久化日志和 pending 更新；pending 不会因迁移被删除。 */
    private MigrationState mergeLatestState(DocumentDO document) {
        long basePersistedLogId = persistedLogId(document);
        List<DocumentOpLogDO> durableUpdates = readDurableUpdates(document.getId(), basePersistedLogId);
        List<StoredDocumentPendingUpdate> pendingUpdates = redisRepository.readPendingUpdates(document.getId(),
                Integer.MAX_VALUE);
        byte[] state = snapshotStorage.read(document.getContentObjectKey());
        boolean changed = false;
        long registeredNodeCount = 0L;

        List<byte[]> durableBytes = durableUpdates.stream().map(DocumentOpLogDO::getUpdateData).toList();
        List<byte[]> pendingBytes = pendingUpdates.stream().map(update -> update.update().updateData()).toList();
        List<List<byte[]>> phases = new ArrayList<>();
        phases.addAll(chunkUpdates(durableBytes));
        phases.addAll(chunkUpdates(pendingBytes));
        if (phases.isEmpty()) {
            phases.add(List.of());
        }

        for (List<byte[]> updates : phases) {
            YjsNodeIdentityMigrationResult result = yjsMergeClient.migrateNodeIdentity(state, updates);
            state = result.mergedState();
            changed = changed || result.changed();
            registeredNodeCount = result.registeredNodeCount();
        }

        log.debug("document node identity migration state prepared documentId={} durableUpdates={} pendingUpdates={} "
                        + "registeredNodeCount={} changed={}",
                document.getId(), durableUpdates.size(), pendingUpdates.size(), registeredNodeCount, changed);
        long cutoffLogId = durableUpdates.isEmpty()
                ? basePersistedLogId
                : durableUpdates.get(durableUpdates.size() - 1).getId();
        return new MigrationState(state, changed, cutoffLogId);
    }

    /** 分页读取快照位点之后的持久化日志，并校验日志 ID 与二进制更新有效。 */
    private List<DocumentOpLogDO> readDurableUpdates(long documentId, long basePersistedLogId) {
        int batchSize = requirePositive(properties.getFlushLog().getBatchSize(), "flushLog batchSize");
        List<DocumentOpLogDO> updates = new ArrayList<>();
        long cursor = basePersistedLogId;
        while (true) {
            List<DocumentOpLogDO> batch = documentOpLogMapper.selectByDocumentIdAfterId(documentId, cursor, batchSize);
            if (batch == null || batch.isEmpty()) {
                return List.copyOf(updates);
            }
            for (DocumentOpLogDO update : batch) {
                if (update == null || update.getId() == null || update.getId() <= cursor
                        || update.getUpdateData() == null || update.getUpdateData().length == 0) {
                    throw new DocumentNodeIdentityMigrationException(
                            "document op log batch is not a valid ordered Yjs sequence");
                }
                cursor = update.getId();
                updates.add(update);
            }
        }
    }

    /** 按增量字节上限切分请求；单条超限更新仍单独发送以保留原始数据。 */
    private List<List<byte[]>> chunkUpdates(List<byte[]> updates) {
        int maxBytes = requirePositive(properties.getNodeMigration().getMaxUpdateBatchBytes(),
                "node migration maxUpdateBatchBytes");
        if (updates.isEmpty()) {
            return List.of();
        }
        List<List<byte[]>> chunks = new ArrayList<>();
        List<byte[]> current = new ArrayList<>();
        int currentBytes = 0;
        for (byte[] update : updates) {
            if (update == null || update.length == 0) {
                throw new DocumentNodeIdentityMigrationException("Yjs update must not be empty");
            }
            if (!current.isEmpty() && currentBytes + update.length > maxBytes) {
                chunks.add(List.copyOf(current));
                current = new ArrayList<>();
                currentBytes = 0;
            }
            current.add(update);
            currentBytes += update.length;
        }
        if (!current.isEmpty()) {
            chunks.add(List.copyOf(current));
        }
        return List.copyOf(chunks);
    }

    /** CAS 成功后尽力删除已经被新快照覆盖的日志；失败不影响已切换的快照。 */
    private void cleanupCoveredLogs(long documentId, long cutoffLogId) {
        if (cutoffLogId <= 0) {
            return;
        }
        try {
            documentOpLogMapper.deleteByDocumentIdThroughId(documentId, cutoffLogId);
        } catch (RuntimeException exception) {
            log.warn("document node identity migration snapshot advanced but log cleanup failed documentId={} "
                            + "cutoffLogId={} reason={}", documentId, cutoffLogId, exception.getMessage());
        }
    }

    /** CAS 失败时删除本轮写出的孤儿快照；删除失败不掩盖 CAS 结果，交给运维后续清理。 */
    private void discardUnreferencedSnapshot(long documentId, String objectKey) {
        try {
            snapshotStorage.delete(objectKey);
        } catch (RuntimeException exception) {
            log.warn("document node identity migration orphan snapshot cleanup failed documentId={} objectKey={} "
                            + "reason={}", documentId, objectKey, exception.getMessage());
        }
    }

    /** 将空指针位点统一为零。 */
    private static long persistedLogId(DocumentDO document) {
        return document.getPersistedLogId() == null ? 0L : document.getPersistedLogId();
    }

    /** 校验迁移运行参数。 */
    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    /** 保留本轮 Yjs 状态、是否发生补齐和可安全推进的持久化日志位点。 */
    private record MigrationState(byte[] state, boolean changed, long cutoffLogId) {
    }
}
