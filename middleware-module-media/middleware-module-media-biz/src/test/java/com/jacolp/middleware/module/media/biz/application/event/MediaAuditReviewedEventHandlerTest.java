package com.jacolp.middleware.module.media.biz.application.event;

import com.jacolp.middleware.messaging.event.AuditReviewedEvent;
import com.jacolp.middleware.messaging.base.EventSequenceGuard;
import com.jacolp.module.media.biz.application.event.MediaAuditReviewedEventHandler;
import com.jacolp.module.media.biz.infrastructure.persistence.mapper.ImageMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaAuditReviewedEventHandlerTest {
    private EventSequenceGuard sequenceGuard;
    private ImageMapper imageMapper;
    private MediaAuditReviewedEventHandler handler;

    @BeforeEach
    void setUp() {
        sequenceGuard = mock(EventSequenceGuard.class);
        imageMapper = mock(ImageMapper.class);
        handler = new MediaAuditReviewedEventHandler(sequenceGuard, imageMapper);
    }

    @Test
    void imageRejectionUsesConditionalMediaOwnedUpdate() {
        AuditReviewedEvent event = event(10, AuditReviewedEvent.TargetType.IMAGE,
                AuditReviewedEvent.Decision.REJECTED);
        when(sequenceGuard.advance(MediaAuditReviewedEventHandler.CONSUMER_NAME, "IMAGE", 20, 10))
                .thenReturn(true);

        handler.apply(List.of(event));

        verify(imageMapper).updateAuditStatusIfCurrent(20L, (short) 1, (short) 3);
    }

    @Test
    void ignoresNonImageAndStaleImageEvents() {
        AuditReviewedEvent note = event(10, AuditReviewedEvent.TargetType.NOTE,
                AuditReviewedEvent.Decision.APPROVED);
        AuditReviewedEvent image = event(9, AuditReviewedEvent.TargetType.IMAGE,
                AuditReviewedEvent.Decision.APPROVED);
        when(sequenceGuard.advance(MediaAuditReviewedEventHandler.CONSUMER_NAME, "IMAGE", 20, 9))
                .thenReturn(false);

        handler.apply(List.of(note, image));

        verify(imageMapper, never()).updateAuditStatusIfCurrent(20L, (short) 1, (short) 2);
    }

    private static AuditReviewedEvent event(long auditId, AuditReviewedEvent.TargetType type,
                                             AuditReviewedEvent.Decision decision) {
        return new AuditReviewedEvent(auditId, type, 20L, decision, 9L, null, Instant.now());
    }
}
