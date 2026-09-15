package com.jacolp.media.api.command;

import com.jacolp.media.api.model.MediaAuditDecision;

import java.util.List;
import java.util.Objects;

/**
 * A single decision applied atomically to a batch of media files.
 */
public record ApplyMediaAuditCommand(List<Long> mediaIds, MediaAuditDecision decision, boolean updateRelationStatus) {

    public ApplyMediaAuditCommand(List<Long> mediaIds, MediaAuditDecision decision) {
        this(mediaIds, decision, true);
    }

    public ApplyMediaAuditCommand {
        mediaIds = List.copyOf(Objects.requireNonNull(mediaIds, "mediaIds"));
        decision = Objects.requireNonNull(decision, "decision");
    }
}
