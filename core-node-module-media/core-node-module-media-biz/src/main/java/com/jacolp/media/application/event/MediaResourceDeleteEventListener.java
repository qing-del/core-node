package com.jacolp.media.application.event;

import com.jacolp.common.messaging.constant.EventTypes;
import com.jacolp.common.messaging.event.MediaResourceDeleteRequestedEvent;
import com.jacolp.common.messaging.base.EventEnvelope;
import com.jacolp.common.messaging.tools.EventMessageCodec;
import com.jacolp.common.messaging.pulisher.EventRetryPublisher;
import com.jacolp.common.messaging.constant.EventTopology;
import com.jacolp.common.messaging.service.InboxService;

import java.util.List;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class MediaResourceDeleteEventListener {
    private final EventMessageCodec codec;
    private final InboxService inboxService;
    private final EventRetryPublisher retryPublisher;
    private final MediaResourceDeleteEventHandler handler;

    public MediaResourceDeleteEventListener(EventMessageCodec codec, InboxService inboxService,
            EventRetryPublisher retryPublisher, MediaResourceDeleteEventHandler handler) {
        this.codec = codec;
        this.inboxService = inboxService;
        this.retryPublisher = retryPublisher;
        this.handler = handler;
    }

    @RabbitListener(queues = EventTopology.MEDIA_DELETE_QUEUE)
    public void onMessage(Message message) {
        List<MediaResourceDeleteRequestedEvent> events = null;
        try {
            EventEnvelope envelope = codec.decode(message);
            if (!EventTypes.MEDIA_RESOURCE_DELETE_REQUESTED.equals(envelope.eventType())) {
                throw new IllegalArgumentException("Unsupported media-delete event type: " + envelope.eventType());
            }
            events = codec.payloadItems(envelope, MediaResourceDeleteRequestedEvent.class);
            List<MediaResourceDeleteRequestedEvent> payload = events;
            inboxService.consume(envelope, MediaResourceDeleteEventHandler.CONSUMER_NAME,
                    ignored -> handler.apply(envelope.eventId(), payload));
        } catch (RuntimeException failure) {
            boolean retrying = retryPublisher.retryOrDeadLetter(
                    EventTopology.MEDIA_DELETE_QUEUE, message, failure);
            if (events != null) handler.recordFailure(events, failure, !retrying);
        }
    }
}
