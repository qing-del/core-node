package com.jacolp.common.messaging.pulisher;

import java.util.List;
import java.util.UUID;

import com.jacolp.common.messaging.event.EmailSendRequestedEvent;
import com.jacolp.common.messaging.constant.EventTypes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 邮件发送事件发布器：在调用方事务内写入 Outbox，由 OutboxRelay 异步投递。 */
@Service
public class EmailSendEventPublisher {
    private final OutboxEventPublisher outboxEventPublisher;

    public EmailSendEventPublisher(OutboxEventPublisher outboxEventPublisher) {
        this.outboxEventPublisher = outboxEventPublisher;
    }

    /** Publishes one envelope per recipient so one SMTP failure never replays a successful recipient. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int publish(List<EmailSendRequestedEvent> requests) {
        if (requests == null || requests.isEmpty()) return 0;
        String correlationId = UUID.randomUUID().toString();
        requests.forEach(request -> outboxEventPublisher.publish(EventTypes.EMAIL_SEND_REQUESTED,
                EventTypes.EMAIL_SEND_REQUESTED, "EMAIL", request.businessKey(), correlationId, request));
        return requests.size();
    }
}
