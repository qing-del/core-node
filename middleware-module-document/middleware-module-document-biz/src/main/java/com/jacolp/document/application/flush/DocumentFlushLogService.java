package com.jacolp.document.application.flush;

import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentOpLogDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentOpLogMapper;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.StoredDocumentPendingUpdate;
import com.jacolp.document.metrics.DocumentMetrics;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 将一段受大小限制的已接收二进制更新从 Redis 写入持久化 MySQL 操作日志。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentFlushLogService {

    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Shanghai");

    private final DocumentRedisRepository documentRedisRepository;
    private final DocumentOpLogMapper documentOpLogMapper;
    private final TransactionTemplate transactionTemplate;
    private final DocumentProperties properties;
    private final DocumentMetrics metrics;

    /** 创建不记录指标的刷盘服务，保留与带指标构造器相同的持久化语义。 */
    public DocumentFlushLogService(DocumentRedisRepository documentRedisRepository,
                                   DocumentOpLogMapper documentOpLogMapper,
                                   TransactionTemplate transactionTemplate,
                                   DocumentProperties properties) {
        this(documentRedisRepository, documentOpLogMapper, transactionTemplate, properties, DocumentMetrics.noop());
    }

    /** 创建带指标记录能力的 Redis Stream 刷盘服务。 */
    @Autowired
    public DocumentFlushLogService(DocumentRedisRepository documentRedisRepository,
                                   DocumentOpLogMapper documentOpLogMapper,
                                   TransactionTemplate transactionTemplate,
                                   DocumentProperties properties, DocumentMetrics metrics) {
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository, "documentRedisRepository must not be null");
        this.documentOpLogMapper = Objects.requireNonNull(documentOpLogMapper, "documentOpLogMapper must not be null");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /**
     * 持久化 Redis Stream 的一个有界前缀，并仅在数据库事务提交后删除这段前缀。
     * 数据库对 Redis 操作 ID 和客户端操作 ID 都有唯一约束，因此重复回放不会产生重复记录。
     */
    public DocumentFlushLogResult flush(long documentId) {
        requirePositive(documentId);
        Timer.Sample sample = metrics.startFlush();
        boolean failed = true;
        try {
            List<StoredDocumentPendingUpdate> pending = documentRedisRepository.readPendingUpdates(documentId,
                    Math.max(1, properties.getFlushLog().getBatchSize()));
            FlushBatch batch = toBoundedBatch(documentId, pending);
            if (batch.logs().isEmpty()) {
                // 没有待刷盘更新时保持成功语义，消费者可以安全结束本次 FLUSH_LOG。
                failed = false;
                return DocumentFlushLogResult.empty(documentId);
            }

            // 先把选中的 Stream 前缀写入数据库；事务返回后才删除 Redis 条目，
            // 进程在两者之间崩溃时，重放的重复操作仍能被安全忽略。
            transactionTemplate.execute(status -> {
                documentOpLogMapper.insertBatchIgnoringDuplicates(batch.logs());
                return null;
            });
            long deleted = documentRedisRepository.deletePendingUpdates(documentId, batch.redisOpIds());
            failed = false;
            return new DocumentFlushLogResult(documentId, batch.logs().size(), batch.binaryBytes(), deleted);
        } finally {
            metrics.completeFlush(sample, failed);
        }
    }

    /** 从待刷盘列表中截取数量和字节数均受限的前缀。 */
    private FlushBatch toBoundedBatch(long documentId, List<StoredDocumentPendingUpdate> pending) {
        int maxBytes = properties.getFlushLog().getMaxBatchBytes();
        if (maxBytes <= 0) {
            // 字节上限是保护数据库事务和单次内存占用的必要边界，配置错误不能静默放开批次。
            throw new IllegalStateException("jacolp.document.flush-log.max-batch-bytes must be positive");
        }
        List<DocumentOpLogDO> logs = new ArrayList<>();
        List<String> redisOpIds = new ArrayList<>();
        long bytes = 0L;
        for (StoredDocumentPendingUpdate pendingUpdate : pending) {
            int updateBytes = pendingUpdate.update().updateData().length;
            // 即使第一条更新超过软批次上限也要写入，避免一条可恢复的 Stream 记录
            // 永久阻塞其后的所有更新。
            if (!logs.isEmpty() && bytes + updateBytes > maxBytes) {
                break;
            }
            logs.add(toLog(documentId, pendingUpdate));
            redisOpIds.add(pendingUpdate.redisOpId());
            bytes += updateBytes;
        }
        return new FlushBatch(List.copyOf(logs), List.copyOf(redisOpIds), bytes);
    }

    /** 将 Redis 待持久化模型映射为 MySQL 操作日志模型。 */
    private static DocumentOpLogDO toLog(long documentId, StoredDocumentPendingUpdate pending) {
        return new DocumentOpLogDO(null, documentId, pending.redisOpId(), pending.update().clientUpdateId(),
                pending.update().updateData(), pending.update().operatorId(), pending.update().operatorType(),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(pending.update().createdAt()), APPLICATION_ZONE));
    }

    /** 拒绝没有明确文档范围的刷盘调用。 */
    private static void requirePositive(long documentId) {
        if (documentId <= 0) {
            // 刷盘调用必须绑定明确文档，否则可能读写错误的 Redis Stream key。
            throw new IllegalArgumentException("documentId must be positive");
        }
    }

    /** 一次刷盘选择的日志、对应 Redis ID 及其二进制总字节数。 */
    private record FlushBatch(List<DocumentOpLogDO> logs, List<String> redisOpIds, long binaryBytes) {
    }
}
