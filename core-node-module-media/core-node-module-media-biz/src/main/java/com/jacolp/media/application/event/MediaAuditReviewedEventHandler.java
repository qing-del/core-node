package com.jacolp.media.application.event;

import com.jacolp.media.infrastructure.persistence.mapper.ImageMapper;
import com.jacolp.common.messaging.base.EventSequenceGuard;
import com.jacolp.common.messaging.event.AuditReviewedEvent;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class MediaAuditReviewedEventHandler {
    public static final String CONSUMER_NAME = "media.audit-reviewed";
    private static final short AUDITING = 1;
    private static final short APPROVED = 2;
    private static final short REJECTED = 3;

    private final EventSequenceGuard sequenceGuard;
    private final ImageMapper imageMapper;

    public MediaAuditReviewedEventHandler(EventSequenceGuard sequenceGuard, ImageMapper imageMapper) {
        this.sequenceGuard = sequenceGuard;
        this.imageMapper = imageMapper;
    }

    public void apply(List<AuditReviewedEvent> events) {
        events.stream().filter(event -> event.targetType() == AuditReviewedEvent.TargetType.IMAGE)
                .forEach(this::applyImage);
    }

    private void applyImage(AuditReviewedEvent event) {
        if (!sequenceGuard.advance(CONSUMER_NAME, event.targetType().name(),
                event.targetId(), event.auditId())) return;
        short status = event.decision() == AuditReviewedEvent.Decision.APPROVED ? APPROVED : REJECTED;
        imageMapper.updateAuditStatusIfCurrent(event.targetId(), AUDITING, status);
    }
}
