package com.jacolp.middleware.messaging.constant;

/** Public business event names. Values are wire contracts and must remain stable. */
public final class EventTypes {
    // 审核结果（过审/拒绝）：审核模块发布，笔记/媒体模块消费后异步应用到本地状态
    public static final String AUDIT_REVIEWED = "audit.reviewed";
    // 存储额度释放
    public static final String STORAGE_RELEASED = "storage.released";
    // 媒体资源（图片）删除异步任务
    public static final String MEDIA_RESOURCE_DELETE_REQUESTED = "media.resource.delete-requested";
    // 邮件发送请求
    public static final String EMAIL_SEND_REQUESTED = "email.send-requested";
    // 用户资料变更：用于审核模块维护用户展示投影
    public static final String USER_PROFILE_CHANGED = "user.profile-changed";

    private EventTypes() {
    }
}
