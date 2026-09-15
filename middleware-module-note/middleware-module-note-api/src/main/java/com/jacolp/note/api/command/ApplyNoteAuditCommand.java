package com.jacolp.note.api.command;

import com.jacolp.note.api.model.AuditDecision;

import java.util.List;
import java.util.Objects;

/**
 * A single decision applied atomically to a batch of notes.
 */
public record ApplyNoteAuditCommand(List<Long> noteIds, AuditDecision decision) {

    public ApplyNoteAuditCommand {
        noteIds = List.copyOf(Objects.requireNonNull(noteIds, "noteIds"));
        decision = Objects.requireNonNull(decision, "decision");
    }
}
