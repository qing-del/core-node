package com.jacolp.enums;

import lombok.Getter;

/**
 * 通用非笔记审核状态机。
 */
@Getter
public enum AuditStatus {
    WAIT((short) 0, "待审核"),
    AUDITING((short) 1, "审核中"),
    APPROVED((short) 2, "已通过"),
    REJECTED((short) 3, "已拒绝"),
    DELETED((short) 4, "已删除");

    private final Short code;
    private final String desc;

    AuditStatus(Short code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static AuditStatus fromCode(Short code) {
        if (code == null) {
            return null;
        }
        for (AuditStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid AuditStatus code: " + code);
    }

    public boolean canTransitionTo(AuditStatus target) {
        if (target == null || this == DELETED) {
            return false;
        }
        if (target == DELETED) {
            return this == WAIT || this == APPROVED || this == REJECTED;
        }
        switch (this) {
            case WAIT:
            case REJECTED:
                return target == AUDITING;
            case AUDITING:
                return target == WAIT || target == APPROVED || target == REJECTED;
            default:
                return false;
        }
    }

    public boolean isVisible() {
        return this != DELETED;
    }
}
