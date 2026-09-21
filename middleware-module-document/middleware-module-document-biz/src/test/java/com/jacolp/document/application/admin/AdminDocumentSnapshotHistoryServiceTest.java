package com.jacolp.document.application.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.document.application.compact.DocumentSnapshotStorage;
import com.jacolp.document.controller.AdminSnapshotHistoryClearResponse;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.framework.minio.MinioObjectSummary;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminDocumentSnapshotHistoryServiceTest {

    @Test
    void deletesOnlyHistoricalSnapshotsAndSumsReleasedBytes() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        DocumentSnapshotStorage storage = mock(DocumentSnapshotStorage.class);
        DocumentDO document = document(7L, "document/7/state/current.bin");
        when(mapper.selectActiveByIds(List.of(7L))).thenReturn(List.of(document));
        when(mapper.selectActiveById(7L)).thenReturn(document);
        when(storage.listHistorical(7L, "document/7/state/current.bin")).thenReturn(List.of(
                new MinioObjectSummary("document/7/state/old-a.bin", 7L),
                new MinioObjectSummary("document/7/state/old-b.bin", 13L)));

        AdminSnapshotHistoryClearResponse response = new AdminDocumentSnapshotHistoryService(mapper, storage)
                .clear(List.of(7L, 7L));

        assertThat(response.deletedObjectCount()).isEqualTo(2L);
        assertThat(response.releasedBytes()).isEqualTo(20L);
        assertThat(response.documents()).singleElement().satisfies(item -> {
            assertThat(item.success()).isTrue();
            assertThat(item.deletedObjectCount()).isEqualTo(2L);
            assertThat(item.releasedBytes()).isEqualTo(20L);
        });
        verify(storage).delete("document/7/state/old-a.bin");
        verify(storage).delete("document/7/state/old-b.bin");
        verify(storage, never()).delete("document/7/state/current.bin");
    }

    @Test
    void returnsPartialReleasedSpaceWhenOneDeleteFails() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        DocumentSnapshotStorage storage = mock(DocumentSnapshotStorage.class);
        DocumentDO document = document(7L, "document/7/state/current.bin");
        when(mapper.selectActiveByIds(List.of(7L))).thenReturn(List.of(document));
        when(mapper.selectActiveById(7L)).thenReturn(document);
        when(storage.listHistorical(eq(7L), any())).thenReturn(List.of(
                new MinioObjectSummary("document/7/state/old-a.bin", 7L),
                new MinioObjectSummary("document/7/state/old-b.bin", 13L)));
        doThrow(new RuntimeException("MinIO unavailable")).when(storage).delete("document/7/state/old-b.bin");

        AdminSnapshotHistoryClearResponse response = new AdminDocumentSnapshotHistoryService(mapper, storage)
                .clear(List.of(7L));

        assertThat(response.deletedObjectCount()).isEqualTo(1L);
        assertThat(response.releasedBytes()).isEqualTo(7L);
        assertThat(response.documents()).singleElement().satisfies(item -> {
            assertThat(item.success()).isFalse();
            assertThat(item.failureMessage()).contains("可重试");
        });
    }

    @Test
    void rejectsWholeBatchBeforeTouchingObjectStorageWhenAnyDocumentIsNotActive() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        DocumentSnapshotStorage storage = mock(DocumentSnapshotStorage.class);
        when(mapper.selectActiveByIds(List.of(7L, 8L))).thenReturn(List.of(document(7L, null)));

        assertThatThrownBy(() -> new AdminDocumentSnapshotHistoryService(mapper, storage).clear(List.of(7L, 8L)))
                .isInstanceOf(BaseException.class)
                .hasMessage("文档不存在或已删除");
        verify(storage, never()).listHistorical(any(Long.class), any());
    }

    private static DocumentDO document(long id, String currentObjectKey) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 20, 12, 0);
        return new DocumentDO(id, 10L, "Plan", currentObjectKey, 0L, now, 10L, false, 0L, now, now);
    }
}
