package com.jacolp.note.application.event;

import com.jacolp.common.messaging.base.EventSequenceGuard;
import com.jacolp.common.messaging.event.AuditReviewedEvent;
import com.jacolp.note.infrastructure.persistence.mapper.NoteEachMappingMapper;
import com.jacolp.note.infrastructure.persistence.mapper.NoteImageMappingMapper;
import com.jacolp.note.infrastructure.persistence.mapper.NoteMapper;
import com.jacolp.note.infrastructure.persistence.mapper.NoteTagMappingMapper;
import com.jacolp.note.infrastructure.persistence.mapper.TagMapper;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class NoteAuditReviewedEventHandler {
    public static final String CONSUMER_NAME = "note.audit-reviewed";
    private static final short NOTE_PENDING_AUDIT = 4;
    private static final short NOTE_APPROVED = 5;
    private static final short NOTE_REJECTED = 7;
    private static final short RESOURCE_AUDITING = 1;
    private static final short RESOURCE_APPROVED = 2;
    private static final short RESOURCE_REJECTED = 3;
    private static final short NOTE_RELATION_APPROVED = 1;
    private static final short NOTE_RELATION_REJECTED = 2;

    private final EventSequenceGuard sequenceGuard;
    private final NoteMapper noteMapper;
    private final TagMapper tagMapper;
    private final NoteEachMappingMapper noteEachMappingMapper;
    private final NoteTagMappingMapper noteTagMappingMapper;
    private final NoteImageMappingMapper noteImageMappingMapper;

    public NoteAuditReviewedEventHandler(EventSequenceGuard sequenceGuard, NoteMapper noteMapper,
            TagMapper tagMapper, NoteEachMappingMapper noteEachMappingMapper,
            NoteTagMappingMapper noteTagMappingMapper, NoteImageMappingMapper noteImageMappingMapper) {
        this.sequenceGuard = sequenceGuard;
        this.noteMapper = noteMapper;
        this.tagMapper = tagMapper;
        this.noteEachMappingMapper = noteEachMappingMapper;
        this.noteTagMappingMapper = noteTagMappingMapper;
        this.noteImageMappingMapper = noteImageMappingMapper;
    }

    public void apply(List<AuditReviewedEvent> events) {
        events.forEach(this::applyOne);
    }

    private void applyOne(AuditReviewedEvent event) {
        if (!sequenceGuard.advance(CONSUMER_NAME, event.targetType().name(),
                event.targetId(), event.auditId())) return;
        switch (event.targetType()) {
            case NOTE -> applyNote(event);
            case TAG -> applyTag(event);
            case IMAGE -> noteImageMappingMapper.updateActiveByImageId(event.targetId(), resourceStatus(event));
        }
    }

    private void applyNote(AuditReviewedEvent event) {
        short status = event.decision() == AuditReviewedEvent.Decision.APPROVED
                ? NOTE_APPROVED : NOTE_REJECTED;
        if (noteMapper.updateStatusIfCurrent(event.targetId(), NOTE_PENDING_AUDIT, status) == 1) {
            noteEachMappingMapper.updateActiveByTargetNoteId(event.targetId(),
                    event.decision() == AuditReviewedEvent.Decision.APPROVED
                            ? NOTE_RELATION_APPROVED : NOTE_RELATION_REJECTED);
        }
    }

    private void applyTag(AuditReviewedEvent event) {
        short status = resourceStatus(event);
        if (tagMapper.updateAuditStatusIfCurrent(event.targetId(), RESOURCE_AUDITING, status) == 1) {
            noteTagMappingMapper.updateActiveByTagId(event.targetId(), status);
        }
    }

    private static short resourceStatus(AuditReviewedEvent event) {
        return event.decision() == AuditReviewedEvent.Decision.APPROVED
                ? RESOURCE_APPROVED : RESOURCE_REJECTED;
    }
}
