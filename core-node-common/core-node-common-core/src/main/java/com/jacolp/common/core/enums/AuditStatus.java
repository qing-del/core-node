package com.jacolp.common.core.enums;

import lombok.Getter;

/** Shared lifecycle for independently owned auditable resources. */
@Getter
public enum AuditStatus {
    CANCELLED((short) 5, "\u5df2\u64a4\u9500"),
    WAIT((short) 0, "待审核"),
    AUDITING((short) 1, "审核中"),
    APPROVED((short) 2, "已通过"),
    REJECTED((short) 3, "已拒绝"),
    DELETED((short) 4, "已删除");

    private final Short code;
    private final String desc;

    AuditStatus(Short code, String desc) { this.code = code; this.desc = desc; }

    public static AuditStatus fromCode(Short code) {
        if (code == null) return null;
        for (AuditStatus status : values()) if (status.code.equals(code)) return status;
        throw new IllegalArgumentException("Invalid AuditStatus code: " + code);
    }

    public boolean canTransitionTo(AuditStatus target) {
        if (target == null || this == DELETED) return false;
        if (target == DELETED) return this == WAIT || this == APPROVED || this == REJECTED;
        return switch (this) {
            case WAIT, REJECTED -> target == AUDITING;
            case AUDITING -> target == WAIT || target == APPROVED || target == REJECTED;
            default -> false;
        };
    }

    public boolean isVisible() { return this != DELETED; }
}
