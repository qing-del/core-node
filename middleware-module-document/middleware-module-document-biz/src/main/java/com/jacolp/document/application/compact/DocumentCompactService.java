package com.jacolp.document.application.compact;

import com.jacolp.document.application.yjs.YjsMergeClient;
import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentOpLogDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentOpLogMapper;
import com.jacolp.document.metrics.DocumentMetrics;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 将一段已持久化的日志位点压缩成一个新的不可变 Yjs 快照。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentCompactService {

    private static final Logger log = LoggerFactory.getLogger(DocumentCompactService.class);

    private final DocumentMapper documentMapper;
    private final DocumentOpLogMapper documentOpLogMapper;
    private final DocumentSnapshotStorage snapshotStorage;
    private final YjsMergeClient yjsMergeClient;
    private final DocumentProperties properties;
    private final DocumentMetrics metrics;

    /** 创建不记录指标的压缩服务，供测试或简化装配使用。 */
    public DocumentCompactService(DocumentMapper documentMapper, DocumentOpLogMapper documentOpLogMapper,
                                  DocumentSnapshotStorage snapshotStorage, YjsMergeClient yjsMergeClient,
                                  DocumentProperties properties) {
        this(documentMapper, documentOpLogMapper, snapshotStorage, yjsMergeClient, properties, DocumentMetrics.noop());
    }

    /** 创建带指标记录能力的快照压缩服务。 */
    @Autowired
    public DocumentCompactService(DocumentMapper documentMapper, DocumentOpLogMapper documentOpLogMapper,
                                  DocumentSnapshotStorage snapshotStorage, YjsMergeClient yjsMergeClient,
                                  DocumentProperties properties, DocumentMetrics metrics) {
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper must not be null");
        this.documentOpLogMapper = Objects.requireNonNull(documentOpLogMapper, "documentOpLogMapper must not be null");
        this.snapshotStorage = Objects.requireNonNull(snapshotStorage, "snapshotStorage must not be null");
        this.yjsMergeClient = Objects.requireNonNull(yjsMergeClient, "yjsMergeClient must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /** 读取一个有界日志批次，写入不可变快照并用 MySQL CAS 切换读取指针。 */
    public DocumentCompactResult compact(long documentId) {
        if (documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
        Timer.Sample sample = metrics.startCompact();
        boolean failed = true;
        try {
            DocumentDO document = documentMapper.selectById(documentId);
            if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
                // 文档不存在或已软删除时没有可继续压缩的有效状态，按幂等无更新返回。
                failed = false;
                return DocumentCompactResult.noUpdates(documentId);
            }
            long basePersistedLogId = document.getPersistedLogId() == null ? 0L : document.getPersistedLogId();
            List<DocumentOpLogDO> updates = documentOpLogMapper.selectByDocumentIdAfterId(documentId, basePersistedLogId,
                    Math.max(1, properties.getFlushLog().getBatchSize()));
            if (updates == null || updates.isEmpty()) {
                // 当前 Snapshot 位点之后没有日志，避免无意义调用合并服务和生成空对象。
                failed = false;
                return DocumentCompactResult.noUpdates(documentId);
            }
            // 先验证日志严格递增且有内容，确保 cutoff 能准确表达本轮合并的边界。
            long cutoffLogId = requireOrderedCutoff(basePersistedLogId, updates);
            // 合并当前不可变快照和一段有序日志后写入新对象；CAS 成功前，读取方仍通过旧指针读取旧快照。
            byte[] mergedState = yjsMergeClient.merge(snapshotStorage.read(document.getContentObjectKey()),
                    updates.stream().map(DocumentOpLogDO::getUpdateData).toList());
            String objectKey = snapshotStorage.write(documentId, mergedState);
            metrics.recordSnapshotBytes(mergedState.length);
            int casAffected = documentMapper.updateSnapshotPointerIfPersistedLogId(documentId, basePersistedLogId,
                    objectKey, cutoffLogId);
            if (casAffected != 1) {
                // 另一轮压缩已先更新快照指针；本轮写出的对象不会被引用，也不会删除属于较新状态的日志。
                failed = false;
                return new DocumentCompactResult(documentId, DocumentCompactResult.Status.CAS_LOST, cutoffLogId, objectKey);
            }
            try {
                documentOpLogMapper.deleteByDocumentIdThroughId(documentId, cutoffLogId);
            } catch (RuntimeException exception) {
                // 新快照指针已使这些日志在语义上过期；后续重试或清理任务可再删除它们。
                log.warn("Snapshot pointer advanced but op log cleanup failed for documentId={}, cutoffLogId={}: {}",
                        documentId, cutoffLogId, exception.getMessage());
            }
            failed = false;
            return new DocumentCompactResult(documentId, DocumentCompactResult.Status.COMPACTED, cutoffLogId, objectKey);
        } finally {
            metrics.completeCompact(sample, failed);
        }
    }

    /** 校验压缩批次连续、有序且每条更新都是非空二进制内容。 */
    private static long requireOrderedCutoff(long basePersistedLogId, List<DocumentOpLogDO> updates) {
        long previousId = basePersistedLogId;
        for (DocumentOpLogDO update : updates) {
            if (update.getId() == null || update.getId() <= previousId || update.getUpdateData() == null
                    || update.getUpdateData().length == 0) {
                // cutoff 必须落在严格递增且非空的日志边界上，否则 CAS 指针可能跳过无法恢复的更新。
                throw new IllegalStateException("document op log batch is not a valid ordered Yjs cutoff");
            }
            previousId = update.getId();
        }
        return previousId;
    }
}
