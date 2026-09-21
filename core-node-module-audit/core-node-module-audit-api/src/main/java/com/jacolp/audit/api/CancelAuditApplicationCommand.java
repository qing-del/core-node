package com.jacolp.audit.api;

import java.util.Objects;

/**
 * 撤销审核申请。
 *
 * @param actorUserId 发起撤销的用户 ID，由调用方显式传入，不能从线程上下文读取
 */
public record CancelAuditApplicationCommand(
        AuditTargetType targetType,
        Long targetId,
        Long actorUserId) {

    public CancelAuditApplicationCommand {
        targetType = Objects.requireNonNull(targetType, "targetType must not be null");
        targetId = requirePositiveId(targetId, "targetId");
        actorUserId = requirePositiveId(actorUserId, "actorUserId");
    }

    private static Long requirePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return id;
    }
}
