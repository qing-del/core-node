package com.jacolp.common.messaging.event;

import java.time.Instant;
import java.util.Objects;

/** Business-only audit result contract; no module-specific database status codes are exposed. */
public record AuditReviewedEvent(
        long auditId,
        TargetType targetType,
        long targetId,
        Decision decision,
        long reviewerUserId,
        String rejectReason,
        Instant reviewTime) {

    public AuditReviewedEvent {
        if (auditId <= 0) throw new IllegalArgumentException("auditId must be positive");
        Objects.requireNonNull(targetType, "targetType must not be null");
        if (targetId <= 0) throw new IllegalArgumentException("targetId must be positive");
        Objects.requireNonNull(decision, "decision must not be null");
        if (reviewerUserId <= 0) throw new IllegalArgumentException("reviewerUserId must be positive");
        Objects.requireNonNull(reviewTime, "reviewTime must not be null");
    }

    public enum TargetType { NOTE, TAG, IMAGE }

    public enum Decision { APPROVED, REJECTED }
}
