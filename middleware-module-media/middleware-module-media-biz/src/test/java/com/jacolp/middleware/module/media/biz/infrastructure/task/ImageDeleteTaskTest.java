package com.jacolp.middleware.module.media.biz.infrastructure.task;

import com.jacolp.constant.ImageConstant;
import com.jacolp.common.messaging.event.MediaResourceDeleteRequestedEvent;
import com.jacolp.common.messaging.pulisher.MediaResourceDeleteEventPublisher;
import com.jacolp.media.infrastructure.persistence.dataobject.ImageDeleteDeadLetterDO;
import com.jacolp.media.infrastructure.persistence.mapper.ImageDeleteDeadLetterMapper;
import com.jacolp.media.infrastructure.task.ImageDeleteTask;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageDeleteTaskTest {

    @Test
    void migratesLegacyRowsToReliableDeleteEventsWithoutCallingStorage() {
        ImageDeleteDeadLetterMapper mapper = mock(ImageDeleteDeadLetterMapper.class);
        MediaResourceDeleteEventPublisher publisher = mock(MediaResourceDeleteEventPublisher.class);
        ImageDeleteDeadLetterDO row = new ImageDeleteDeadLetterDO();
        row.setId(9L);
        row.setImageUrl("https://bucket.example/image/7/a.png");
        when(mapper.selectBatch(ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_WAITING))
                .thenReturn(List.of(row));
        when(mapper.markQueued(List.of(9L), ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_QUEUED))
                .thenReturn(1);

        new ImageDeleteTask(mapper, publisher).deleteImageTask();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MediaResourceDeleteRequestedEvent>> events = ArgumentCaptor.forClass(List.class);
        verify(publisher).publish(events.capture());
        assertThat(events.getValue()).containsExactly(
                new MediaResourceDeleteRequestedEvent("legacy:9", "image/7/a.png", 9L));
        verify(mapper).markQueued(List.of(9L), ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_QUEUED);
    }

    @Test
    void recordsMalformedLegacyUrlsForManualRepair() {
        ImageDeleteDeadLetterMapper mapper = mock(ImageDeleteDeadLetterMapper.class);
        MediaResourceDeleteEventPublisher publisher = mock(MediaResourceDeleteEventPublisher.class);
        ImageDeleteDeadLetterDO row = new ImageDeleteDeadLetterDO();
        row.setId(9L);
        row.setImageUrl("https://bucket.example/other/a.png");
        when(mapper.selectBatch(ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_WAITING))
                .thenReturn(List.of(row));

        new ImageDeleteTask(mapper, publisher).deleteImageTask();

        verify(mapper).markFailed(9L, ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_FAILED,
                "Legacy image URL does not contain the configured image prefix");
        verify(publisher).publish(List.of());
    }
}
