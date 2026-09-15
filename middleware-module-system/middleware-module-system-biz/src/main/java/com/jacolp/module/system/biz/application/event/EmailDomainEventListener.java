package com.jacolp.module.system.biz.application.event;

import com.jacolp.middleware.messaging.event.EmailSendRequestedEvent;
import com.jacolp.middleware.messaging.base.EventEnvelope;
import com.jacolp.middleware.messaging.tools.EventMessageCodec;
import com.jacolp.middleware.messaging.pulisher.EventRetryPublisher;
import com.jacolp.middleware.messaging.constant.EventTopology;
import com.jacolp.middleware.messaging.constant.EventTypes;
import com.jacolp.middleware.messaging.service.InboxService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class EmailDomainEventListener {
    private final EventMessageCodec codec;
    private final InboxService inboxService;
    private final EventRetryPublisher retryPublisher;
    private final EmailSendRequestedEventHandler handler;

    public EmailDomainEventListener(EventMessageCodec codec, InboxService inboxService,
            EventRetryPublisher retryPublisher, EmailSendRequestedEventHandler handler) {
        this.codec = codec;
        this.inboxService = inboxService;
        this.retryPublisher = retryPublisher;
        this.handler = handler;
    }

    @RabbitListener(queues = EventTopology.EMAIL_QUEUE)
    public void onMessage(Message message) {
        try {
            EventEnvelope envelope = codec.decode(message);
            if (!EventTypes.EMAIL_SEND_REQUESTED.equals(envelope.eventType())) {
                throw new IllegalArgumentException("Unsupported email event type: " + envelope.eventType());
            }
            EmailSendRequestedEvent request = codec.payload(envelope, EmailSendRequestedEvent.class);
            inboxService.consume(envelope, EmailSendRequestedEventHandler.CONSUMER_NAME,
                    ignored -> handler.apply(request));
        } catch (RuntimeException failure) {
            retryPublisher.retryOrDeadLetter(EventTopology.EMAIL_QUEUE, message, failure);
        }
    }
}
