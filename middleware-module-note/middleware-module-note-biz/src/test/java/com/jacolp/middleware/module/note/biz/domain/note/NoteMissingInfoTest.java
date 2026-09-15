package com.jacolp.middleware.module.note.biz.domain.note;

import com.jacolp.enums.NoteMissingInfoMask;
import com.jacolp.enums.NoteStatus;
import com.jacolp.module.note.biz.domain.note.NoteMissingInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoteMissingInfoTest {

    @Test
    void supportsAllThreeMissingKindsAndDoesNotDecreaseCountBelowZero() {
        NoteMissingInfo tag = new NoteMissingInfo(0, 2)
                .afterPartialBind(NoteMissingInfoMask.TAG, 5, true);
        NoteMissingInfo image = new NoteMissingInfo(0, 2)
                .afterPartialBind(NoteMissingInfoMask.IMAGE, 5, true);
        NoteMissingInfo note = new NoteMissingInfo(0, 2)
                .afterPartialBind(NoteMissingInfoMask.NOTE, 5, true);

        assertEquals(0, tag.count());
        assertEquals(0, image.count());
        assertEquals(0, note.count());
        assertEquals(0, tag.mask());
        assertEquals(0, image.mask());
        assertEquals(0, note.mask());
    }

    @Test
    void unbindMarksKindIncreasesCountAndMovesReadyNoteToPendingInfo() {
        NoteMissingInfo updated = new NoteMissingInfo(NoteMissingInfoMask.IMAGE.getMask(), 3)
                .afterUnbind(NoteMissingInfoMask.TAG);

        assertEquals(NoteMissingInfoMask.IMAGE.getMask() | NoteMissingInfoMask.TAG.getMask(), updated.mask());
        assertEquals(4, updated.count());
        assertEquals(NoteStatus.PENDING_INFO.getCode(),
                updated.statusAfterUnbind(NoteStatus.READY_TO_CONVERT.getCode()));
        assertEquals(NoteStatus.NEW.getCode(), updated.statusAfterUnbind(NoteStatus.NEW.getCode()));
    }

    @Test
    void preservesExistingPartialBindMaskCondition() {
        NoteMissingInfo current = new NoteMissingInfo(NoteMissingInfoMask.TAG.getMask(), 2);
        NoteMissingInfo updated = current.afterPartialBind(NoteMissingInfoMask.TAG, 1, true);

        assertEquals(0, updated.mask());
        assertEquals(1, updated.count());
        assertFalse(current.shouldRecalculateAfterBind(1));
        assertTrue(current.shouldRecalculateAfterBind(2));
        assertTrue(updated.shouldRecalculateAfterBind(1));
    }
}
