package com.jacolp.middleware.messaging.pulisher;

import java.util.List;
import java.util.UUID;

import com.jacolp.middleware.messaging.constant.EventTypes;
import com.jacolp.middleware.messaging.event.StorageReleasedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 存储额度释放事件发布器：对象逻辑删除后异步释放用户存储配额。 */
@Service
public class StorageReleasedEventPublisher {
    private final OutboxEventPublisher outboxEventPublisher;

    public StorageReleasedEventPublisher(OutboxEventPublisher outboxEventPublisher) {
        this.outboxEventPublisher = outboxEventPublisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(List<StorageReleasedEvent> events) {
        if (events == null || events.isEmpty()) return;
        outboxEventPublisher.publishPartitioned(EventTypes.STORAGE_RELEASED, EventTypes.STORAGE_RELEASED,
                "STORAGE_RESOURCE", events.getFirst().resourceId(), UUID.randomUUID().toString(), events);
    }
}
