package com.jacolp.note.domain.note;

import com.jacolp.note.enums.NoteMissingInfoMask;
import com.jacolp.note.enums.NoteStatus;

/** Pure lifecycle predicates for Note application orchestration. */
public final class NoteLifecyclePolicy {

    private NoteLifecyclePolicy() {
    }

    public static boolean hasBlockingMissingInfo(Integer missingCount, int missingMask) {
        return missingCount != null && missingCount > 0
                && NoteMissingInfoMask.isComplete(missingMask);
    }

    public static boolean canConvert(NoteStatus currentStatus) {
        return currentStatus.canTransitionTo(NoteStatus.CONVERTED);
    }

    public static boolean canTransition(NoteStatus currentStatus, NoteStatus targetStatus) {
        return currentStatus.canTransitionTo(targetStatus);
    }

    public static boolean requiresAllMappingsPassed(NoteStatus targetStatus) {
        return targetStatus == NoteStatus.PUBLISHED;
    }

    public static boolean canPublish(NoteStatus targetStatus, boolean allMappingsPassed) {
        return targetStatus != NoteStatus.PUBLISHED || allMappingsPassed;
    }
}
