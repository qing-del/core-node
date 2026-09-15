package com.jacolp.audit.api;

import java.util.Objects;

/**
 * 撤销审核申请后的稳定返回值。
 */
public record CancelAuditApplicationResult(
        AuditTargetType targetType,
        Long targetId,
        Long actorUserId,
        int cancelledCount) {

    public CancelAuditApplicationResult {
        targetType = Objects.requireNonNull(targetType, "targetType must not be null");
        targetId = requirePositiveId(targetId, "targetId");
        actorUserId = requirePositiveId(actorUserId, "actorUserId");
        if (cancelledCount < 0) {
            throw new IllegalArgumentException("cancelledCount must not be negative");
        }
    }

    private static Long requirePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return id;
    }
}
