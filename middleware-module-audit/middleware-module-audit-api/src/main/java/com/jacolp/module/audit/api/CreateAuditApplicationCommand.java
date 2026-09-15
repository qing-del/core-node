package com.jacolp.module.audit.api;

import java.util.Objects;

/**
 * 创建审核申请。
 *
 * @param applicantUserId 申请人 ID，由调用方显式传入，不能从线程上下文读取
 */
public record CreateAuditApplicationCommand(
        AuditTargetType targetType,
        Long targetId,
        Long applicantUserId,
        String applyReason) {

    public CreateAuditApplicationCommand {
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
