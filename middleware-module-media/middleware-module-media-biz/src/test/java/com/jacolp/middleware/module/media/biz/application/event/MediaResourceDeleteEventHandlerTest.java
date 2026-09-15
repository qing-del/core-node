package com.jacolp.middleware.module.media.biz.application.event;

import com.jacolp.constant.ImageConstant;
import com.jacolp.framework.oss.AliyunOSSOperator;
import com.jacolp.middleware.messaging.event.MediaResourceDeleteRequestedEvent;
import com.jacolp.module.media.biz.application.event.MediaResourceDeleteEventHandler;
import com.jacolp.module.media.biz.infrastructure.persistence.mapper.ImageDeleteDeadLetterMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaResourceDeleteEventHandlerTest {

    @Test
    void marksTrackingCompleteOnlyAfterObjectStorageConfirmsDeletion() {
        AliyunOSSOperator oss = mock(AliyunOSSOperator.class);
        ImageDeleteDeadLetterMapper mapper = mock(ImageDeleteDeadLetterMapper.class);
        MediaResourceDeleteRequestedEvent event =
                new MediaResourceDeleteRequestedEvent("23", "image/7/a.png", 9L);
        when(mapper.attachEvent(9L, "event-1", ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_QUEUED))
                .thenReturn(1);
        when(oss.delete("image/7/a.png")).thenReturn(true);
        when(mapper.markCompleted(9L, ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_COMPLETED))
                .thenReturn(1);

        new MediaResourceDeleteEventHandler(oss, mapper).apply("event-1", List.of(event));

        verify(mapper).markCompleted(9L, ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_COMPLETED);
    }

    @Test
    void throwsOnStorageFailureSoTheListenerCanRetry() {
        AliyunOSSOperator oss = mock(AliyunOSSOperator.class);
        ImageDeleteDeadLetterMapper mapper = mock(ImageDeleteDeadLetterMapper.class);
        MediaResourceDeleteRequestedEvent event =
                new MediaResourceDeleteRequestedEvent("23", "image/7/a.png", 9L);
        when(mapper.attachEvent(9L, "event-1", ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_QUEUED))
                .thenReturn(1);
        when(oss.delete("image/7/a.png")).thenReturn(false);

        assertThatThrownBy(() -> new MediaResourceDeleteEventHandler(oss, mapper)
                .apply("event-1", List.of(event)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("storage deletion failed");
        verify(mapper, never()).markCompleted(9L, ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_COMPLETED);
    }
}
