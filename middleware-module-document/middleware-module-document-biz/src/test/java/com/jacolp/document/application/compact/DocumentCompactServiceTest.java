package com.jacolp.document.application.compact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.document.application.yjs.YjsMergeClient;
import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentOpLogDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentOpLogMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DocumentCompactServiceTest {

    @Test
    void writesImmutableSnapshotThenAdvancesPointerWithCasBeforeCleaningLogs() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentOpLogMapper opLogMapper = mock(DocumentOpLogMapper.class);
        DocumentSnapshotStorage snapshotStorage = mock(DocumentSnapshotStorage.class);
        YjsMergeClient mergeClient = mock(YjsMergeClient.class);
        when(documentMapper.selectById(7L)).thenReturn(document(7L, null, 5L));
        when(opLogMapper.selectByDocumentIdAfterId(7L, 5L, 500)).thenReturn(List.of(
                update(6L, new byte[] {1}), update(7L, new byte[] {2})));
        when(mergeClient.merge(isNull(), anyList())).thenReturn(new byte[] {9, 8});
        when(snapshotStorage.write(eq(7L), any(byte[].class))).thenReturn("document/7/state/new.bin");
        when(documentMapper.updateSnapshotPointerIfPersistedLogId(7L, 5L, "document/7/state/new.bin", 7L)).thenReturn(1);

        DocumentCompactResult result = new DocumentCompactService(documentMapper, opLogMapper, snapshotStorage,
                mergeClient, new DocumentProperties()).compact(7L);

        assertThat(result).isEqualTo(new DocumentCompactResult(7L, DocumentCompactResult.Status.COMPACTED,
                7L, "document/7/state/new.bin"));
        ArgumentCaptor<byte[]> mergedState = ArgumentCaptor.forClass(byte[].class);
        verify(snapshotStorage).write(eq(7L), mergedState.capture());
        assertThat(mergedState.getValue()).containsExactly(9, 8);
        ArgumentCaptor<List<byte[]>> mergedUpdates = ArgumentCaptor.forClass(List.class);
        verify(mergeClient).merge(isNull(), mergedUpdates.capture());
        assertThat(mergedUpdates.getValue()).containsExactly(new byte[] {1}, new byte[] {2});
        verify(documentMapper).updateSnapshotPointerIfPersistedLogId(7L, 5L, "document/7/state/new.bin", 7L);
        verify(opLogMapper).deleteByDocumentIdThroughId(7L, 7L);
    }

    @Test
    void leavesNewObjectAsOrphanAndDoesNotDeleteLogsWhenCasLosesRace() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentOpLogMapper opLogMapper = mock(DocumentOpLogMapper.class);
        DocumentSnapshotStorage snapshotStorage = mock(DocumentSnapshotStorage.class);
        YjsMergeClient mergeClient = mock(YjsMergeClient.class);
        when(documentMapper.selectById(7L)).thenReturn(document(7L, null, 5L));
        when(opLogMapper.selectByDocumentIdAfterId(7L, 5L, 500)).thenReturn(List.of(update(6L, new byte[] {1})));
        when(mergeClient.merge(isNull(), anyList())).thenReturn(new byte[] {9});
        when(snapshotStorage.write(eq(7L), any(byte[].class))).thenReturn("document/7/state/loser.bin");
        when(documentMapper.updateSnapshotPointerIfPersistedLogId(7L, 5L, "document/7/state/loser.bin", 6L)).thenReturn(0);

        DocumentCompactResult result = new DocumentCompactService(documentMapper, opLogMapper, snapshotStorage,
                mergeClient, new DocumentProperties()).compact(7L);

        assertThat(result.status()).isEqualTo(DocumentCompactResult.Status.CAS_LOST);
        verify(opLogMapper, never()).deleteByDocumentIdThroughId(any(), any());
    }

    @Test
    void doesNothingWhenNoDurableUpdateExistsPastCurrentPointer() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentOpLogMapper opLogMapper = mock(DocumentOpLogMapper.class);
        when(documentMapper.selectById(7L)).thenReturn(document(7L, null, 5L));
        when(opLogMapper.selectByDocumentIdAfterId(7L, 5L, 500)).thenReturn(List.of());

        DocumentCompactResult result = new DocumentCompactService(documentMapper, opLogMapper,
                mock(DocumentSnapshotStorage.class), mock(YjsMergeClient.class), new DocumentProperties()).compact(7L);

        assertThat(result).isEqualTo(DocumentCompactResult.noUpdates(7L));
    }

    @Test
    void keepsAdvancedPointerWhenBestEffortLogCleanupFails() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentOpLogMapper opLogMapper = mock(DocumentOpLogMapper.class);
        DocumentSnapshotStorage snapshotStorage = mock(DocumentSnapshotStorage.class);
        YjsMergeClient mergeClient = mock(YjsMergeClient.class);
        when(documentMapper.selectById(7L)).thenReturn(document(7L, null, 5L));
        when(opLogMapper.selectByDocumentIdAfterId(7L, 5L, 500)).thenReturn(List.of(update(6L, new byte[] {1})));
        when(mergeClient.merge(isNull(), anyList())).thenReturn(new byte[] {9});
        when(snapshotStorage.write(eq(7L), any(byte[].class))).thenReturn("document/7/state/winner.bin");
        when(documentMapper.updateSnapshotPointerIfPersistedLogId(7L, 5L, "document/7/state/winner.bin", 6L)).thenReturn(1);
        doThrow(new IllegalStateException("cleanup unavailable")).when(opLogMapper).deleteByDocumentIdThroughId(7L, 6L);

        DocumentCompactResult result = new DocumentCompactService(documentMapper, opLogMapper, snapshotStorage,
                mergeClient, new DocumentProperties()).compact(7L);

        assertThat(result.status()).isEqualTo(DocumentCompactResult.Status.COMPACTED);
        verify(documentMapper).updateSnapshotPointerIfPersistedLogId(7L, 5L, "document/7/state/winner.bin", 6L);
    }

    private static DocumentDO document(long id, String objectKey, long persistedLogId) {
        LocalDateTime now = LocalDateTime.now();
        return new DocumentDO(id, 42L, "title", objectKey, persistedLogId, now, 42L, false, 0L, now, now);
    }

    private static DocumentOpLogDO update(long id, byte[] update) {
        return new DocumentOpLogDO(id, 7L, id + "-0", "123e4567-e89b-12d3-a456-426614174000", update,
                42L, "user", LocalDateTime.now());
    }
}
