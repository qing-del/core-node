package com.jacolp.document.application.close;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.document.application.compact.DocumentCompactResult;
import com.jacolp.document.application.compact.DocumentCompactService;
import com.jacolp.document.application.binding.DocumentBindingConsumeResult;
import com.jacolp.document.application.binding.DocumentBindingConsumer;
import com.jacolp.document.application.flush.DocumentFlushLogResult;
import com.jacolp.document.application.flush.DocumentFlushLogService;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.websocket.DocumentRoomManager;
import com.jacolp.document.websocket.DocumentSessionPresenceRegistry;
import org.junit.jupiter.api.Test;

class DocumentCloseServiceTest {

    @Test
    void finalizesAllFlushAndCompactBatchesThenClearsRuntimeState() {
        DocumentRoomLifecycleService lifecycle = mock(DocumentRoomLifecycleService.class);
        DocumentSessionPresenceRegistry presence = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomManager rooms = mock(DocumentRoomManager.class);
        DocumentFlushLogService flush = mock(DocumentFlushLogService.class);
        DocumentBindingConsumer binding = mock(DocumentBindingConsumer.class);
        DocumentCompactService compact = mock(DocumentCompactService.class);
        DocumentRedisRepository redis = mock(DocumentRedisRepository.class);
        when(lifecycle.isCurrentClose(7L, "token")).thenReturn(true, true);
        when(presence.count(7L)).thenReturn(0L, 0L);
        when(rooms.hasNoLocalSessions(7L)).thenReturn(true, true, true);
        when(rooms.beginClosingIfEmpty(7L)).thenReturn(true);
        when(flush.flush(7L)).thenReturn(new DocumentFlushLogResult(7L, 1, 1L, 1L),
                DocumentFlushLogResult.empty(7L));
        when(binding.drain(7L)).thenReturn(new DocumentBindingConsumeResult(7L, 1, 1L),
                DocumentBindingConsumeResult.empty(7L));
        when(compact.compact(7L)).thenReturn(new DocumentCompactResult(7L, DocumentCompactResult.Status.COMPACTED,
                1L, "new"), DocumentCompactResult.noUpdates(7L));
        DocumentCloseService service = new DocumentCloseService(lifecycle, presence, rooms, flush, binding, compact, redis);

        DocumentCloseResult result = service.close(7L, "token");

        assertThat(result.status()).isEqualTo(DocumentCloseResult.Status.CLOSED);
        verify(rooms).removeIfEmpty(7L);
        verify(redis).deleteRoomRuntime(7L);
        verify(binding, times(2)).drain(7L);
    }

    @Test
    void doesNotClearRuntimeWhenSessionReappearsDuringFinalPersistence() {
        DocumentRoomLifecycleService lifecycle = mock(DocumentRoomLifecycleService.class);
        DocumentSessionPresenceRegistry presence = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomManager rooms = mock(DocumentRoomManager.class);
        DocumentFlushLogService flush = mock(DocumentFlushLogService.class);
        DocumentBindingConsumer binding = mock(DocumentBindingConsumer.class);
        DocumentCompactService compact = mock(DocumentCompactService.class);
        DocumentRedisRepository redis = mock(DocumentRedisRepository.class);
        when(lifecycle.isCurrentClose(7L, "token")).thenReturn(true, true);
        when(presence.count(7L)).thenReturn(0L, 1L);
        when(rooms.hasNoLocalSessions(7L)).thenReturn(true, true);
        when(rooms.beginClosingIfEmpty(7L)).thenReturn(true);
        when(flush.flush(7L)).thenReturn(DocumentFlushLogResult.empty(7L));
        when(binding.drain(7L)).thenReturn(DocumentBindingConsumeResult.empty(7L));
        when(compact.compact(7L)).thenReturn(DocumentCompactResult.noUpdates(7L));
        DocumentCloseService service = new DocumentCloseService(lifecycle, presence, rooms, flush, binding, compact, redis);

        DocumentCloseResult result = service.close(7L, "token");

        assertThat(result.status()).isEqualTo(DocumentCloseResult.Status.REOPENED);
        verify(redis, never()).deleteRoomRuntime(7L);
        verify(rooms, never()).removeIfEmpty(7L);
    }

    @Test
    void doesNotClearRuntimeWhenBindingStreamIsStillNonEmpty() {
        DocumentRoomLifecycleService lifecycle = mock(DocumentRoomLifecycleService.class);
        DocumentSessionPresenceRegistry presence = mock(DocumentSessionPresenceRegistry.class);
        DocumentRoomManager rooms = mock(DocumentRoomManager.class);
        DocumentFlushLogService flush = mock(DocumentFlushLogService.class);
        DocumentBindingConsumer binding = mock(DocumentBindingConsumer.class);
        DocumentCompactService compact = mock(DocumentCompactService.class);
        DocumentRedisRepository redis = mock(DocumentRedisRepository.class);
        when(lifecycle.isCurrentClose(7L, "token")).thenReturn(true, true);
        when(presence.count(7L)).thenReturn(0L, 0L);
        when(rooms.hasNoLocalSessions(7L)).thenReturn(true, true, true);
        when(rooms.beginClosingIfEmpty(7L)).thenReturn(true);
        when(flush.flush(7L)).thenReturn(DocumentFlushLogResult.empty(7L));
        when(binding.drain(7L)).thenReturn(DocumentBindingConsumeResult.empty(7L));
        when(compact.compact(7L)).thenReturn(DocumentCompactResult.noUpdates(7L));
        when(redis.pendingUpdateCount(7L)).thenReturn(0L);
        when(redis.pendingBindingCount(7L)).thenReturn(1L);
        DocumentCloseService service = new DocumentCloseService(lifecycle, presence, rooms, flush, binding, compact, redis);

        DocumentCloseResult result = service.close(7L, "token");

        assertThat(result.status()).isEqualTo(DocumentCloseResult.Status.REOPENED);
        verify(redis, never()).deleteRoomRuntime(7L);
        verify(rooms, never()).removeIfEmpty(7L);
    }
}
