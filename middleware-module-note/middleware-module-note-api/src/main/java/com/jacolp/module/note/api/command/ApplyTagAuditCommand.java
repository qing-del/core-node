package com.jacolp.module.note.api.command;

import com.jacolp.module.note.api.model.AuditDecision;

import java.util.List;
import java.util.Objects;

/**
 * A single decision applied atomically to a batch of tags.
 */
public record ApplyTagAuditCommand(List<Long> tagIds, AuditDecision decision) {

    public ApplyTagAuditCommand {
        tagIds = List.copyOf(Objects.requireNonNull(tagIds, "tagIds"));
        decision = Objects.requireNonNull(decision, "decision");
    }
}
