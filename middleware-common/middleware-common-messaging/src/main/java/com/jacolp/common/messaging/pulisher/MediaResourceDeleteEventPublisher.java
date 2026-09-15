package com.jacolp.common.messaging.pulisher;

import java.util.List;
import java.util.UUID;

import com.jacolp.common.messaging.constant.EventTypes;
import com.jacolp.common.messaging.event.MediaResourceDeleteRequestedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 媒体资源删除事件发布器：删除 OSS 对象为异步任务，通过 Outbox 保证最终一致。 */
@Service
public class MediaResourceDeleteEventPublisher {
    private final OutboxEventPublisher outboxEventPublisher;

    public MediaResourceDeleteEventPublisher(OutboxEventPublisher outboxEventPublisher) {
        this.outboxEventPublisher = outboxEventPublisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(List<MediaResourceDeleteRequestedEvent> events) {
        if (events == null || events.isEmpty()) return;
        outboxEventPublisher.publishPartitioned(EventTypes.MEDIA_RESOURCE_DELETE_REQUESTED,
                EventTypes.MEDIA_RESOURCE_DELETE_REQUESTED, "MEDIA_RESOURCE",
                events.getFirst().resourceId(), UUID.randomUUID().toString(), events);
    }
}
