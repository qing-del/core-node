package com.jacolp.module.audit.api;

import java.util.Objects;

/**
 * 查询某个业务对象是否存在待审核申请。
 */
public record PendingAuditApplicationQuery(
        AuditTargetType targetType,
        Long targetId) {

    public PendingAuditApplicationQuery {
        targetType = Objects.requireNonNull(targetType, "targetType must not be null");
        targetId = requirePositiveId(targetId, "targetId");
    }

    private static Long requirePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return id;
    }
}
