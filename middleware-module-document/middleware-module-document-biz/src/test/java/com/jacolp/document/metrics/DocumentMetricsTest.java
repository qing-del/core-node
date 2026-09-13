package com.jacolp.document.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentOpLogMapper;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.DocumentRoomMeta;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentMetricsTest {

    @Test
    void registersRequiredLowCardinalityMetricsAndAggregatesActiveRuntimeState() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DocumentRedisRepository redis = mock(DocumentRedisRepository.class);
        DocumentMapper documents = mock(DocumentMapper.class);
        DocumentOpLogMapper operationLogs = mock(DocumentOpLogMapper.class);
        when(redis.findRoomMetas()).thenReturn(List.of(new DocumentRoomMeta(7L, 42L, false, null, 0L, 42L)));
        when(redis.pendingUpdateCount(7L)).thenReturn(2L);
        when(redis.pendingBindingCount(7L)).thenReturn(4L);
        when(documents.selectById(7L)).thenReturn(document(7L, 42L));
        when(operationLogs.countByDocumentIdAfterId(7L, 0L)).thenReturn(3L);
        DocumentMetrics metrics = new DocumentMetrics(registry, redis, documents, operationLogs);

        metrics.updateRuntimeCounts(1, 2);
        metrics.recordUpdateAccepted();
        metrics.recordUpdateRejected();
        Timer.Sample flush = metrics.startFlush();
        metrics.completeFlush(flush, false);
        Timer.Sample compact = metrics.startCompact();
        metrics.completeCompact(compact, true);
        metrics.recordSnapshotBytes(123L);
        Timer.Sample merge = metrics.startYjsMerge();
        metrics.completeYjsMerge(merge, false);
        metrics.recordCloseFailed();

        assertThat(registry.get("document_ws_sessions").gauge().value()).isEqualTo(2D);
        assertThat(registry.get("document_active_rooms").gauge().value()).isEqualTo(1D);
        assertThat(registry.get("document_pending_update_count").gauge().value()).isEqualTo(2D);
        assertThat(registry.get("document_pending_binding_count").gauge().value()).isEqualTo(4D);
        assertThat(registry.get("document_unmerged_op_count").gauge().value()).isEqualTo(3D);
        assertThat(registry.get("document_update_accept_total").counter().count()).isEqualTo(1D);
        assertThat(registry.get("document_update_reject_total").counter().count()).isEqualTo(1D);
        assertThat(registry.get("document_flush_log_duration").timer().count()).isEqualTo(1L);
        assertThat(registry.get("document_compact_duration").timer().count()).isEqualTo(1L);
        assertThat(registry.get("document_compact_failed_total").counter().count()).isEqualTo(1D);
        assertThat(registry.get("document_snapshot_bytes").summary().totalAmount()).isEqualTo(123D);
        assertThat(registry.get("yjs_merge_service_duration").timer().count()).isEqualTo(1L);
        assertThat(registry.get("document_close_failed_total").counter().count()).isEqualTo(1D);
    }

    private static DocumentDO document(long id, long ownerUserId) {
        LocalDateTime now = LocalDateTime.now();
        return new DocumentDO(id, ownerUserId, "title", null, 0L, now, ownerUserId, false, 0L, now, now);
    }
}
