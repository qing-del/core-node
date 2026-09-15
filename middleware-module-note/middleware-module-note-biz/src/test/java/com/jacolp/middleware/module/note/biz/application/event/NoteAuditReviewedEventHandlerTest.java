package com.jacolp.middleware.module.note.biz.application.event;

import com.jacolp.common.messaging.base.EventSequenceGuard;
import com.jacolp.common.messaging.event.AuditReviewedEvent;
import com.jacolp.note.application.event.NoteAuditReviewedEventHandler;
import com.jacolp.note.infrastructure.persistence.mapper.NoteEachMappingMapper;
import com.jacolp.note.infrastructure.persistence.mapper.NoteImageMappingMapper;
import com.jacolp.note.infrastructure.persistence.mapper.NoteMapper;
import com.jacolp.note.infrastructure.persistence.mapper.NoteTagMappingMapper;
import com.jacolp.note.infrastructure.persistence.mapper.TagMapper;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoteAuditReviewedEventHandlerTest {
    private EventSequenceGuard sequenceGuard;
    private NoteMapper noteMapper;
    private TagMapper tagMapper;
    private NoteEachMappingMapper noteEachMappingMapper;
    private NoteTagMappingMapper noteTagMappingMapper;
    private NoteImageMappingMapper noteImageMappingMapper;
    private NoteAuditReviewedEventHandler handler;

    @BeforeEach
    void setUp() {
        sequenceGuard = mock(EventSequenceGuard.class);
        noteMapper = mock(NoteMapper.class);
        tagMapper = mock(TagMapper.class);
        noteEachMappingMapper = mock(NoteEachMappingMapper.class);
        noteTagMappingMapper = mock(NoteTagMappingMapper.class);
        noteImageMappingMapper = mock(NoteImageMappingMapper.class);
        handler = new NoteAuditReviewedEventHandler(sequenceGuard, noteMapper, tagMapper,
                noteEachMappingMapper, noteTagMappingMapper, noteImageMappingMapper);
    }

    @Test
    void noteApprovalConditionallyUpdatesNoteAndRelations() {
        AuditReviewedEvent event = event(10, AuditReviewedEvent.TargetType.NOTE, 20,
                AuditReviewedEvent.Decision.APPROVED);
        when(sequenceGuard.advance(NoteAuditReviewedEventHandler.CONSUMER_NAME, "NOTE", 20, 10))
                .thenReturn(true);
        when(noteMapper.updateStatusIfCurrent(20L, (short) 4, (short) 5)).thenReturn(1);

        handler.apply(List.of(event));

        verify(noteMapper).updateStatusIfCurrent(20L, (short) 4, (short) 5);
        verify(noteEachMappingMapper).updateActiveByTargetNoteId(20L, (short) 1);
    }

    @Test
    void staleSequenceProducesNoBusinessSideEffect() {
        AuditReviewedEvent event = event(9, AuditReviewedEvent.TargetType.IMAGE, 20,
                AuditReviewedEvent.Decision.REJECTED);
        when(sequenceGuard.advance(NoteAuditReviewedEventHandler.CONSUMER_NAME, "IMAGE", 20, 9))
                .thenReturn(false);

        handler.apply(List.of(event));

        verify(noteImageMappingMapper, never()).updateActiveByImageId(20L, (short) 3);
    }

    @Test
    void tagRelationDoesNotChangeWhenTagLeftAuditingState() {
        AuditReviewedEvent event = event(10, AuditReviewedEvent.TargetType.TAG, 20,
                AuditReviewedEvent.Decision.REJECTED);
        when(sequenceGuard.advance(NoteAuditReviewedEventHandler.CONSUMER_NAME, "TAG", 20, 10))
                .thenReturn(true);
        when(tagMapper.updateAuditStatusIfCurrent(20L, (short) 1, (short) 3)).thenReturn(0);

        handler.apply(List.of(event));

        verify(noteTagMappingMapper, never()).updateActiveByTagId(20L, (short) 3);
    }

    @Test
    void imageEventUpdatesOnlyNoteOwnedRelationProjection() {
        AuditReviewedEvent event = event(10, AuditReviewedEvent.TargetType.IMAGE, 20,
                AuditReviewedEvent.Decision.APPROVED);
        when(sequenceGuard.advance(NoteAuditReviewedEventHandler.CONSUMER_NAME, "IMAGE", 20, 10))
                .thenReturn(true);

        handler.apply(List.of(event));

        verify(noteImageMappingMapper).updateActiveByImageId(20L, (short) 2);
        verify(tagMapper, never()).updateAuditStatusIfCurrent(20L, (short) 1, (short) 2);
    }

    private static AuditReviewedEvent event(long auditId, AuditReviewedEvent.TargetType type,
                                             long targetId, AuditReviewedEvent.Decision decision) {
        return new AuditReviewedEvent(auditId, type, targetId, decision, 9L, null, Instant.now());
    }
}
