package com.jacolp.document.application.flush;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.DocumentRoomMeta;
import com.jacolp.document.messaging.DocumentSchedulePublisher;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentFlushRecoveryScannerTest {

    @Test
    void reschedulesOnlyRoomsWhosePendingStreamStillContainsUpdates() {
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentSchedulePublisher publisher = mock(DocumentSchedulePublisher.class);
        when(redisRepository.findRoomMetas()).thenReturn(List.of(
                new DocumentRoomMeta(7L, 42L, false, null, 1L, 42L),
                new DocumentRoomMeta(8L, 42L, false, null, 1L, 42L)));
        when(redisRepository.pendingUpdateCount(7L)).thenReturn(1L);
        when(redisRepository.pendingUpdateCount(8L)).thenReturn(0L);
        when(redisRepository.pendingBindingCount(7L)).thenReturn(0L);
        when(redisRepository.pendingBindingCount(8L)).thenReturn(0L);

        new DocumentFlushRecoveryScanner(redisRepository, publisher).scanAndReschedule();

        verify(publisher).scheduleFlushLog(7L);
        verify(publisher, org.mockito.Mockito.never()).scheduleFlushLog(8L);
    }

    @Test
    void reschedulesRoomWhenOnlyBindingStreamStillContainsEntries() {
        DocumentRedisRepository redisRepository = mock(DocumentRedisRepository.class);
        DocumentSchedulePublisher publisher = mock(DocumentSchedulePublisher.class);
        when(redisRepository.findRoomMetas()).thenReturn(List.of(
                new DocumentRoomMeta(7L, 42L, false, null, 1L, 42L)));
        when(redisRepository.pendingUpdateCount(7L)).thenReturn(0L);
        when(redisRepository.pendingBindingCount(7L)).thenReturn(2L);

        new DocumentFlushRecoveryScanner(redisRepository, publisher).scanAndReschedule();

        verify(publisher).scheduleFlushLog(7L);
    }
}
