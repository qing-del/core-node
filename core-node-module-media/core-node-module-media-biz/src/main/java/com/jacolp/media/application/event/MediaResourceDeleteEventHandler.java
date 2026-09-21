package com.jacolp.media.application.event;

import com.jacolp.constant.ImageConstant;
import com.jacolp.framework.oss.AliyunOSSOperator;
import com.jacolp.media.infrastructure.persistence.mapper.ImageDeleteDeadLetterMapper;
import com.jacolp.common.messaging.event.MediaResourceDeleteRequestedEvent;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaResourceDeleteEventHandler {
    public static final String CONSUMER_NAME = "media.resource-delete";

    private final AliyunOSSOperator ossOperator;
    private final ImageDeleteDeadLetterMapper trackingMapper;

    public MediaResourceDeleteEventHandler(AliyunOSSOperator ossOperator,
                                           ImageDeleteDeadLetterMapper trackingMapper) {
        this.ossOperator = ossOperator;
        this.trackingMapper = trackingMapper;
    }

    public void apply(String eventId, List<MediaResourceDeleteRequestedEvent> events) {
        for (MediaResourceDeleteRequestedEvent event : events) {
            if (trackingMapper.attachEvent(event.trackingId(), eventId,
                    ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_QUEUED) != 1) {
                throw new IllegalStateException("Physical-delete tracking record does not exist");
            }
            if (!Boolean.TRUE.equals(ossOperator.delete(event.objectKey()))) {
                throw new IllegalStateException("Object storage deletion failed");
            }
            if (trackingMapper.markCompleted(event.trackingId(),
                    ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_COMPLETED) != 1) {
                throw new IllegalStateException("Unable to complete physical-delete tracking record");
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordFailure(List<MediaResourceDeleteRequestedEvent> events, RuntimeException failure,
                              boolean terminal) {
        String error = safeError(failure);
        events.forEach(event -> trackingMapper.markFailed(event.trackingId(),
                terminal ? ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_FAILED
                        : ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_QUEUED,
                error));
    }

    private static String safeError(RuntimeException failure) {
        String message = failure.getMessage();
        String value = failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
