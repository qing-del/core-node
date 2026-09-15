package com.jacolp.enums;

import lombok.Getter;

/**
 * 笔记状态枚举
 * <p>
 * 状态转换规则（参考状态机图）：
 * <ul>
 *   <li>正常流转：NEW → PENDING_INFO → READY_TO_CONVERT → CONVERTED → PENDING_AUDIT → APPROVED → PUBLISHED</li>
 *   <li>修改笔记：修改笔记后状态回到 NEW</li>
 *   <li>删除规则：除 PENDING_AUDIT 和 PUBLISHED 外，其他状态都可以转到 DELETED</li>
 *   <li>公开/取消公开：APPROVED ↔ PUBLISHED 可以相互转换</li>
 *   <li>拒绝后转换：REJECTED → NEW（用户修改）或 CONVERTED（用户确认）</li>
 *   <li>已通过后修改：APPROVED → NEW（修改已通过笔记）</li>
 * </ul>
 */
@Getter
public enum NoteStatus {
    /**
     * 已创建 - 笔记刚上传，内容已解析但关联信息可能不完整
     */
    NEW((short) 0, "已创建", false, false, false),
    /**
     * 缺失信息 - 存在标签/图片/内联笔记缺失，需要用户补充
     */
    PENDING_INFO((short) 1, "缺失信息", false, false, false),
    /**
     * 待转换 - 关联信息完整，等待管理员执行 Markdown 转 HTML
     */
    READY_TO_CONVERT((short) 2, "待转换", false, false, false),
    /**
     * 已转换 - 管理员已完成 Markdown 到 HTML 的转换
     */
    CONVERTED((short) 3, "已转换", false, false, false),
    /**
     * 审核中 - 用户已提交审核申请，等待管理员审核
     */
    PENDING_AUDIT((short) 4, "审核中", false, false, false),
    /**
     * 已通过 - 审核通过，但尚未公开
     */
    APPROVED((short) 5, "已通过", true, false, false),
    /**
     * 已公开 - 审核通过且已发布，其他用户可见
     */
    PUBLISHED((short) 6, "已公开", true, true, false),
    /**
     * 已拒绝 - 审核未通过
     */
    REJECTED((short) 7, "已拒绝", false, false, false),
    /**
     * 已删除 - 笔记被软删除
     */
    DELETED((short) 8, "已删除", false, false, true);

    private final Short code;
    private final String desc;
    private final boolean approved;
    private final boolean published;
    private final boolean deleted;

    NoteStatus(Short code, String desc, boolean approved, boolean published, boolean deleted) {
        this.code = code;
        this.desc = desc;
        this.approved = approved;
        this.published = published;
        this.deleted = deleted;
    }

    /**
     * 根据 code 获取枚举
     *
     * @param code 状态代码
     * @return 对应的枚举值
     * @throws IllegalArgumentException 无效的状态代码
     */
    public static NoteStatus fromCode(Short code) {
        if (code == null) {
            return null;
        }
        for (NoteStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid NoteStatus code: " + code);
    }

    /**
     * 检查是否已经通过审核
     *
     * @param status 笔记状态
     * @return true=已通过, false=未通过
     */
    public static boolean isPassed(NoteStatus status) {
        return status == APPROVED || status == PUBLISHED;
    }

    /**
     * 检查是否可以绑定/解绑操作
     * @param status 笔记状态
     * @return true=可以绑定, false=不允许绑定
     */
    public static boolean canBindOperation(NoteStatus status) {
        return status == NEW || status == PENDING_INFO || status == READY_TO_CONVERT;
    }

    /**
     * 检查是否可以转换到目标状态
     *
     * @param target 目标状态
     * @return true=可以转换, false=不允许转换
     */
    public boolean canTransitionTo(NoteStatus target) {
        // 删除状态不可以转换到其他状态了（终结态）
        if (this == DELETED) {
            return false;
        }
        // 处于 待审核 / 已公开 状态不可以 删除 / 更新(修改上传)
        if (target == DELETED || target == NEW) {
            return this != PENDING_AUDIT && this != PUBLISHED;
        }


        switch (this) {
            case NEW:   // 创建 -> 缺失信息 | 待转换
                return target == PENDING_INFO || target == READY_TO_CONVERT;
            case PENDING_INFO:  // 缺失信息 -> 待转换
                return target == READY_TO_CONVERT;
            case READY_TO_CONVERT:  // 待转换 -> 已转换
                return target == CONVERTED;
            case CONVERTED: // 已转换 -> 待审核 | 待转换（删除转换缓存）
                return target == PENDING_AUDIT || target == READY_TO_CONVERT;
            case PENDING_AUDIT: // 待审核 -> 已通过 / 拒绝 /  已转换(取消审核)
                return target == APPROVED || target == REJECTED || target == CONVERTED;
            case APPROVED:  // 已通过 -> 已公开
                return target == PUBLISHED;
            case PUBLISHED: // 已公开 -> 已通过
                return target == APPROVED;
            case REJECTED:  // 拒绝 -> 已转换
                return target == CONVERTED;
            default:
                return false;
        }
    }
}
