package com.jacolp.common.core.constant;

/** Legacy wire/database codes shared while module-owned schemas are migrated. */
public final class AuditConstant {
    public static final Short REJECT = 2;
    public static final Short PASS = 1;
    public static final Short WAIT = 0;
    public static final Short CANCEL = 3;
    public static final Short TAG_APPLY_TYPE = 2;
    public static final String DEFAULT_REJECT_REASON = "管理员拒绝了你的申请";

    private AuditConstant() { }
}
