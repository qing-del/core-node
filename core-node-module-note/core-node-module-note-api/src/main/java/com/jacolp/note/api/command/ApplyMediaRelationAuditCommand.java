package com.jacolp.note.api.command;

import com.jacolp.note.api.model.AuditDecision;
import java.util.List;
import java.util.Objects;

/** Applies one audit decision to note-image relation rows in a single batch. */
public record ApplyMediaRelationAuditCommand(List<Long> mediaIds, AuditDecision decision) {
    public ApplyMediaRelationAuditCommand {
        mediaIds = List.copyOf(Objects.requireNonNull(mediaIds, "mediaIds"));
        decision = Objects.requireNonNull(decision, "decision");
    }
}
