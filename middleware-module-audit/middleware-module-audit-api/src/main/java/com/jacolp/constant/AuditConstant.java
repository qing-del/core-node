package com.jacolp.constant;

public class AuditConstant {
    /**
     * Legacy note audit review result codes.
     * Non-note resources use {@link com.jacolp.enums.AuditStatus}.
     */
    public static final Short REJECT = 2;
    public static final Short PASS = 1;
    public static final Short WAIT = 0;

    /* 申请类型 */
    public static final Short TAG_APPLY_TYPE = 2;

    public static final String DEFAULT_REJECT_REASON = "管理员拒绝了你的申请";
}
