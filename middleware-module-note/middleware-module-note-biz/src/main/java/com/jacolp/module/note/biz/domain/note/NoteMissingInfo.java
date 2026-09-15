package com.jacolp.module.note.biz.domain.note;

import com.jacolp.enums.NoteMissingInfoMask;
import com.jacolp.enums.NoteStatus;

/**
 * Value object for the persisted missing-relation mask and count.
 *
 * <p>This class deliberately preserves the existing facade semantics, including
 * its partial-bind mask update rule.</p>
 */
public record NoteMissingInfo(int mask, int count) {

    public boolean shouldRecalculateAfterBind(int affectedRows) {
        return count <= affectedRows;
    }

    public NoteMissingInfo afterPartialBind(NoteMissingInfoMask kind, int affectedRows,
                                            boolean stillMissing) {
        int updatedMask = stillMissing ? mask & ~kind.getMask() : mask;
        return new NoteMissingInfo(updatedMask, Math.max(count - affectedRows, 0));
    }

    public NoteMissingInfo afterUnbind(NoteMissingInfoMask kind) {
        return new NoteMissingInfo(mask | kind.getMask(), count + 1);
    }

    public Short statusAfterUnbind(Short currentStatus) {
        return NoteStatus.READY_TO_CONVERT.getCode().equals(currentStatus)
                ? NoteStatus.PENDING_INFO.getCode()
                : currentStatus;
    }
}
