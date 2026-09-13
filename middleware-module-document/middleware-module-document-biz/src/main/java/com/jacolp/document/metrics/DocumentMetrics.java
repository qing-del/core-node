package com.jacolp.document.metrics;

import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentOpLogMapper;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.DocumentRoomMeta;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 协作持久化链路的低基数遥测指标，避免文档 ID 等高基数标签进入监控系统。 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentMetrics {

    private final MeterRegistry meterRegistry;
    private final DocumentRedisRepository documentRedisRepository;
    private final DocumentMapper documentMapper;
    private final DocumentOpLogMapper documentOpLogMapper;
    private final AtomicInteger websocketSessions = new AtomicInteger();
    private final AtomicInteger activeRooms = new AtomicInteger();
    private final Counter updateAccepted;
    private final Counter updateRejected;
    private final Counter flushFailed;
    private final Counter compactFailed;
    private final Counter closeFailed;
    private final Timer flushDuration;
    private final Timer compactDuration;
    private final Timer yjsMergeDuration;
    private final DistributionSummary snapshotBytes;

    /** 注册低基数 Counter、Timer、Gauge 和快照字节分布指标。 */
    public DocumentMetrics(MeterRegistry meterRegistry, DocumentRedisRepository documentRedisRepository,
                           DocumentMapper documentMapper, DocumentOpLogMapper documentOpLogMapper) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository,
                "documentRedisRepository must not be null");
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper must not be null");
        this.documentOpLogMapper = Objects.requireNonNull(documentOpLogMapper, "documentOpLogMapper must not be null");
        updateAccepted = Counter.builder("document_update_accept_total").register(meterRegistry);
        updateRejected = Counter.builder("document_update_reject_total").register(meterRegistry);
        flushFailed = Counter.builder("document_flush_log_failed_total").register(meterRegistry);
        compactFailed = Counter.builder("document_compact_failed_total").register(meterRegistry);
        closeFailed = Counter.builder("document_close_failed_total").register(meterRegistry);
        flushDuration = Timer.builder("document_flush_log_duration").register(meterRegistry);
        compactDuration = Timer.builder("document_compact_duration").register(meterRegistry);
        yjsMergeDuration = Timer.builder("yjs_merge_service_duration").register(meterRegistry);
        snapshotBytes = DistributionSummary.builder("document_snapshot_bytes").register(meterRegistry);
        Gauge.builder("document_ws_sessions", websocketSessions, AtomicInteger::get).register(meterRegistry);
        Gauge.builder("document_active_rooms", activeRooms, AtomicInteger::get).register(meterRegistry);
        Gauge.builder("document_pending_update_count", this, ignored -> pendingUpdateCount()).register(meterRegistry);
        Gauge.builder("document_pending_binding_count", this, ignored -> pendingBindingCount()).register(meterRegistry);
        Gauge.builder("document_unmerged_op_count", this, ignored -> unmergedOpCount()).register(meterRegistry);
    }

    /** 创建不执行任何指标操作的哨兵实例。 */
    private DocumentMetrics() {
        meterRegistry = null;
        documentRedisRepository = null;
        documentMapper = null;
        documentOpLogMapper = null;
        updateAccepted = null;
        updateRejected = null;
        flushFailed = null;
        compactFailed = null;
        closeFailed = null;
        flushDuration = null;
        compactDuration = null;
        yjsMergeDuration = null;
        snapshotBytes = null;
    }

    /** 返回全局共享的无操作指标实例。 */
    public static DocumentMetrics noop() {
        return NoopHolder.INSTANCE;
    }

    /** 记录一条已写入 Redis 的客户端更新。 */
    public void recordUpdateAccepted() {
        if (meterRegistry != null) updateAccepted.increment();
    }

    /** 记录一条因协议、权限或容量失败而拒绝的客户端更新。 */
    public void recordUpdateRejected() {
        if (meterRegistry != null) updateRejected.increment();
    }

    /** 开始测量一次 FLUSH_LOG 调用；无操作实例返回 null。 */
    public Timer.Sample startFlush() {
        return meterRegistry == null ? null : Timer.start(meterRegistry);
    }

    /** 停止刷盘计时，并在失败时递增失败计数。 */
    public void completeFlush(Timer.Sample sample, boolean failed) {
        if (sample == null) return;
        sample.stop(flushDuration);
        if (failed) flushFailed.increment();
    }

    /** 开始测量一次快照压缩调用。 */
    public Timer.Sample startCompact() {
        return meterRegistry == null ? null : Timer.start(meterRegistry);
    }

    /** 停止压缩计时，并在失败时递增失败计数。 */
    public void completeCompact(Timer.Sample sample, boolean failed) {
        if (sample == null) return;
        sample.stop(compactDuration);
        if (failed) compactFailed.increment();
    }

    /** 开始测量一次 Yjs 合并服务调用。 */
    public Timer.Sample startYjsMerge() {
        return meterRegistry == null ? null : Timer.start(meterRegistry);
    }

    /** 停止 Yjs 合并耗时计时。 */
    public void completeYjsMerge(Timer.Sample sample, boolean failed) {
        if (sample == null) return;
        sample.stop(yjsMergeDuration);
    }

    /** 记录一次最终 CLOSE 失败。 */
    public void recordCloseFailed() {
        if (meterRegistry != null) closeFailed.increment();
    }

    /** 记录快照字节数，忽略负值等非法统计样本。 */
    public void recordSnapshotBytes(long bytes) {
        if (meterRegistry != null && bytes >= 0) snapshotBytes.record(bytes);
    }

    /** 更新当前 JVM 的 Room 和 WebSocket 会话数量。 */
    public void updateRuntimeCounts(int currentActiveRooms, int currentWebsocketSessions) {
        if (meterRegistry == null) return;
        activeRooms.set(Math.max(0, currentActiveRooms));
        websocketSessions.set(Math.max(0, currentWebsocketSessions));
    }

    /** 汇总当前活跃 Room 对应的 Redis 待刷盘更新数。 */
    private double pendingUpdateCount() {
        if (meterRegistry == null) return Double.NaN;
        try {
            return activeDocumentIds().stream().mapToLong(documentRedisRepository::pendingUpdateCount).sum();
        } catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }

    /** 汇总当前活跃 Room 对应的 Redis 待投影 Binding 数量。 */
    private double pendingBindingCount() {
        if (meterRegistry == null) return Double.NaN;
        try {
            return activeDocumentIds().stream().mapToLong(documentRedisRepository::pendingBindingCount).sum();
        } catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }

    /** 汇总当前活跃 Room 对应的 MySQL 未合并操作数。 */
    private double unmergedOpCount() {
        if (meterRegistry == null) return Double.NaN;
        try {
            long total = 0L;
            for (Long documentId : activeDocumentIds()) {
                DocumentDO document = documentMapper.selectById(documentId);
                if (document != null && !Boolean.TRUE.equals(document.getDeleted())) {
                    total += documentOpLogMapper.countByDocumentIdAfterId(documentId,
                            document.getPersistedLogId() == null ? 0L : document.getPersistedLogId());
                }
            }
            return total;
        } catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }

    /** 从 Redis Room Meta 去重得到当前活跃文档集合。 */
    private Set<Long> activeDocumentIds() {
        Set<Long> documentIds = new HashSet<>();
        for (DocumentRoomMeta meta : documentRedisRepository.findRoomMetas()) {
            documentIds.add(meta.documentId());
        }
        return documentIds;
    }

    private static final class NoopHolder {
        private static final DocumentMetrics INSTANCE = new DocumentMetrics();
    }
}
