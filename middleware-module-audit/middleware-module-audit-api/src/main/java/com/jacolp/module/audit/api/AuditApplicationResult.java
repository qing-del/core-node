package com.jacolp.module.audit.api;

import java.util.Objects;

/**
 * 创建审核申请后的稳定返回值，不暴露持久化实体。
 */
public record AuditApplicationResult(
        Long auditApplicationId,
        AuditTargetType targetType,
        Long targetId,
        Long applicantUserId) {

    public AuditApplicationResult {
        auditApplicationId = requirePositiveId(auditApplicationId, "auditApplicationId");
        targetType = Objects.requireNonNull(targetType, "targetType must not be null");
        targetId = requirePositiveId(targetId, "targetId");
        applicantUserId = requirePositiveId(applicantUserId, "applicantUserId");
    }

    private static Long requirePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return id;
    }
}
