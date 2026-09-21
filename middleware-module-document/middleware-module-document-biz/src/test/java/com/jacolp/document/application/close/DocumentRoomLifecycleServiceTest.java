package com.jacolp.document.application.close;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.DocumentRoomMeta;
import com.jacolp.document.messaging.DocumentSchedulePublisher;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DocumentRoomLifecycleServiceTest {

    @Test
    void reopenInvalidatesPriorCloseTokenAndLastLeaveSchedulesNewOne() {
        DocumentRedisRepository repository = mock(DocumentRedisRepository.class);
        DocumentSchedulePublisher publisher = mock(DocumentSchedulePublisher.class);
        when(repository.findRoomMeta(7L)).thenReturn(Optional.of(new DocumentRoomMeta(7L, 42L,
                false, "old", 100L, 42L)));
        DocumentRoomLifecycleService lifecycle = new DocumentRoomLifecycleService(repository, publisher);
        LocalDateTime now = LocalDateTime.now();
        DocumentDO document = new DocumentDO(7L, 42L, "title", null, 0L, now, 42L, false, 0L, now, now);

        lifecycle.reopen(document, 42L);
        lifecycle.requestClose(7L, 42L);

        ArgumentCaptor<DocumentRoomMeta> metas = ArgumentCaptor.forClass(DocumentRoomMeta.class);
        verify(repository, org.mockito.Mockito.times(2)).saveRoomMeta(metas.capture());
        assertThat(metas.getAllValues().getFirst().closeRequested()).isFalse();
        DocumentRoomMeta closing = metas.getAllValues().getLast();
        assertThat(closing.closeRequested()).isTrue();
        assertThat(closing.closeToken()).isNotBlank().isNotEqualTo("old");
        verify(publisher).scheduleClose(7L, closing.closeToken());
    }

    @Test
    void validatesCurrentCloseTokenAgainstRedisRuntimeMeta() {
        DocumentRedisRepository repository = mock(DocumentRedisRepository.class);
        when(repository.findRoomMeta(7L)).thenReturn(Optional.of(new DocumentRoomMeta(7L, 42L,
                true, "token", 100L, 42L)));
        DocumentRoomLifecycleService lifecycle = new DocumentRoomLifecycleService(repository,
                mock(DocumentSchedulePublisher.class));

        assertThat(lifecycle.isCurrentClose(7L, "token")).isTrue();
        assertThat(lifecycle.isCurrentClose(7L, "old")).isFalse();
    }
}
