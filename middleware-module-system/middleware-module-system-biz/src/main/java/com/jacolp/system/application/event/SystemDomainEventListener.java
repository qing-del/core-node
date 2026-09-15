package com.jacolp.system.application.event;

import com.jacolp.common.messaging.constant.EventTypes;
import com.jacolp.common.messaging.event.StorageReleasedEvent;
import com.jacolp.common.messaging.base.EventEnvelope;
import com.jacolp.common.messaging.tools.EventMessageCodec;
import com.jacolp.common.messaging.pulisher.EventRetryPublisher;
import com.jacolp.common.messaging.constant.EventTopology;
import com.jacolp.common.messaging.service.InboxService;
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
