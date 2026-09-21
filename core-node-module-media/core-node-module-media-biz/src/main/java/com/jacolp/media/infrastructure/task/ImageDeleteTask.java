package com.jacolp.media.infrastructure.task;

import com.jacolp.constant.ImageConstant;
import com.jacolp.media.infrastructure.persistence.dataobject.ImageDeleteDeadLetterDO;
import com.jacolp.media.infrastructure.persistence.mapper.ImageDeleteDeadLetterMapper;
import com.jacolp.common.messaging.event.MediaResourceDeleteRequestedEvent;
import com.jacolp.common.messaging.pulisher.MediaResourceDeleteEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class ImageDeleteTask {
    private final ImageDeleteDeadLetterMapper mapper;
    private final MediaResourceDeleteEventPublisher eventPublisher;

    public ImageDeleteTask(ImageDeleteDeadLetterMapper mapper,
                           MediaResourceDeleteEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedRateString = "${jacolp.image.delete-image-task-time:60}", timeUnit = TimeUnit.MINUTES)
    @Transactional(rollbackFor = Exception.class)
    public void deleteImageTask() {
        List<ImageDeleteDeadLetterDO> rows = mapper.selectBatch(ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_WAITING);
        if (rows == null || rows.isEmpty()) return;
        List<Long> queuedIds = new ArrayList<>();
        List<MediaResourceDeleteRequestedEvent> events = new ArrayList<>();
        for (ImageDeleteDeadLetterDO row : rows) {
            String objectKey = extractObjectKey(row.getImageUrl());
            if (objectKey == null) {
                mapper.markFailed(row.getId(), ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_FAILED,
                        "Legacy image URL does not contain the configured image prefix");
                continue;
            }
            String resourceId = row.getResourceId() == null || row.getResourceId().isBlank()
                    ? "legacy:" + row.getId() : row.getResourceId();
            events.add(new MediaResourceDeleteRequestedEvent(resourceId, objectKey, row.getId()));
            queuedIds.add(row.getId());
        }
        eventPublisher.publish(events);
        if (!queuedIds.isEmpty() && mapper.markQueued(queuedIds,
                ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_QUEUED) != queuedIds.size()) {
            throw new IllegalStateException("Unable to mark every legacy physical-delete record as queued");
        }
    }

    static String extractObjectKey(String url) {
        int at = url == null ? -1 : url.indexOf(ImageConstant.IMAGE_OSS_DIRECTORY_PREFIX);
        return at < 0 ? null : url.substring(at);
    }
}
