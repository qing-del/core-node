package com.jacolp.middleware.module.note.biz.domain.note;

import com.jacolp.note.enums.NoteStatus;
import com.jacolp.note.domain.note.NoteLifecyclePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoteLifecyclePolicyTest {

    @Test
    void keepsLifecycleTransitionMatrix() {
        assertTrue(NoteLifecyclePolicy.canTransition(NoteStatus.APPROVED, NoteStatus.PUBLISHED));
        assertTrue(NoteLifecyclePolicy.canTransition(NoteStatus.PUBLISHED, NoteStatus.APPROVED));
        assertFalse(NoteLifecyclePolicy.canTransition(NoteStatus.PENDING_AUDIT, NoteStatus.DELETED));
        assertFalse(NoteLifecyclePolicy.canTransition(NoteStatus.PUBLISHED, NoteStatus.DELETED));
        assertTrue(NoteLifecyclePolicy.canConvert(NoteStatus.READY_TO_CONVERT));
        assertFalse(NoteLifecyclePolicy.canConvert(NoteStatus.NEW));
    }

    @Test
    void requiresCompleteMappingsOnlyForPublish() {
        assertTrue(NoteLifecyclePolicy.requiresAllMappingsPassed(NoteStatus.PUBLISHED));
        assertFalse(NoteLifecyclePolicy.requiresAllMappingsPassed(NoteStatus.APPROVED));
        assertFalse(NoteLifecyclePolicy.canPublish(NoteStatus.PUBLISHED, false));
        assertTrue(NoteLifecyclePolicy.canPublish(NoteStatus.PUBLISHED, true));
        assertTrue(NoteLifecyclePolicy.canPublish(NoteStatus.APPROVED, false));
    }

    @Test
    void preservesExistingSpecialMissingInfoConversionCondition() {
        assertTrue(NoteLifecyclePolicy.hasBlockingMissingInfo(1, 0));
        assertFalse(NoteLifecyclePolicy.hasBlockingMissingInfo(1, 1));
        assertFalse(NoteLifecyclePolicy.hasBlockingMissingInfo(0, 0));
    }
}
