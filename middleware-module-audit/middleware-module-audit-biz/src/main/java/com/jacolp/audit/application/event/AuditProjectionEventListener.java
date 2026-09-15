package com.jacolp.audit.application.event;

import com.jacolp.audit.infrastructure.persistence.mapper.AuditQueryProjectionMapper;
import com.jacolp.common.messaging.constant.EventTypes;
import com.jacolp.common.messaging.event.UserProfileChangedEvent;
import com.jacolp.common.messaging.base.EventEnvelope;
import com.jacolp.common.messaging.tools.EventMessageCodec;
import com.jacolp.common.messaging.pulisher.EventRetryPublisher;
import com.jacolp.common.messaging.constant.EventTopology;
import com.jacolp.common.messaging.service.InboxService;
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
