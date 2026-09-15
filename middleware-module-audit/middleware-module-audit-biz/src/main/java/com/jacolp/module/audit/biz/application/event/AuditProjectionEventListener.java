package com.jacolp.module.audit.biz.application.event;

import com.jacolp.middleware.messaging.base.EventEnvelope;
import com.jacolp.middleware.messaging.tools.EventMessageCodec;
import com.jacolp.middleware.messaging.pulisher.EventRetryPublisher;
import com.jacolp.middleware.messaging.constant.EventTopology;
import com.jacolp.middleware.messaging.constant.EventTypes;
import com.jacolp.middleware.messaging.service.InboxService;
import com.jacolp.middleware.messaging.event.UserProfileChangedEvent;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.AuditQueryProjectionMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 审核模块投影监听器：消费 user.profile-changed，
 * 维护 audit_query_user_projection（用户名/昵称），供审核列表展示快照使用。
 */
@Component
public class AuditProjectionEventListener {
    private final EventMessageCodec codec;
    private final InboxService inbox;
    private final EventRetryPublisher retries;
    private final AuditQueryProjectionMapper projections;

    public AuditProjectionEventListener(EventMessageCodec codec, InboxService inbox,
            EventRetryPublisher retries, AuditQueryProjectionMapper projections) {
        this.codec = codec; this.inbox = inbox; this.retries = retries; this.projections = projections;
    }

    @RabbitListener(queues = EventTopology.AUDIT_PROJECTION_QUEUE)
    public void onMessage(Message message) {
        try {
            EventEnvelope envelope = codec.decode(message);
            if (!EventTypes.USER_PROFILE_CHANGED.equals(envelope.eventType())) {
                throw new IllegalArgumentException("Unsupported audit projection event: " + envelope.eventType());
            }
            UserProfileChangedEvent event = codec.payload(envelope, UserProfileChangedEvent.class);
            inbox.consume(envelope, "audit.user-profile-projection", ignored ->
                    projections.upsertUser(event.userId(), event.username(), event.nickname()));
        } catch (RuntimeException failure) {
            retries.retryOrDeadLetter(EventTopology.AUDIT_PROJECTION_QUEUE, message, failure);
        }
    }
}
