package com.jacolp.module.system.biz.application.event;

import com.jacolp.middleware.messaging.base.EventEnvelope;
import com.jacolp.middleware.messaging.tools.EventMessageCodec;
import com.jacolp.middleware.messaging.pulisher.EventRetryPublisher;
import com.jacolp.middleware.messaging.constant.EventTopology;
import com.jacolp.middleware.messaging.constant.EventTypes;
import com.jacolp.middleware.messaging.service.InboxService;
import com.jacolp.middleware.messaging.event.StorageReleasedEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class SystemDomainEventListener {
    private final EventMessageCodec codec;
    private final InboxService inboxService;
    private final EventRetryPublisher retryPublisher;
    private final StorageReleasedEventHandler storageReleasedHandler;

    public SystemDomainEventListener(EventMessageCodec codec, InboxService inboxService,
            EventRetryPublisher retryPublisher, StorageReleasedEventHandler storageReleasedHandler) {
        this.codec = codec;
        this.inboxService = inboxService;
        this.retryPublisher = retryPublisher;
        this.storageReleasedHandler = storageReleasedHandler;
    }

    @RabbitListener(queues = EventTopology.SYSTEM_QUEUE)
    public void onMessage(Message message) {
        try {
            EventEnvelope envelope = codec.decode(message);
            if (!EventTypes.STORAGE_RELEASED.equals(envelope.eventType())) {
                throw new IllegalArgumentException("Unsupported system event type: " + envelope.eventType());
            }
            inboxService.consume(envelope, StorageReleasedEventHandler.CONSUMER_NAME,
                    ignored -> storageReleasedHandler.apply(
                            codec.payloadItems(envelope, StorageReleasedEvent.class)));
        } catch (RuntimeException failure) {
            retryPublisher.retryOrDeadLetter(EventTopology.SYSTEM_QUEUE, message, failure);
        }
    }
}
