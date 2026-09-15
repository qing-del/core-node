package com.jacolp.middleware.messaging.constant;

/** Central RabbitMQ names; business code must not duplicate literal topology names. */
public final class EventTopology {
    // 领域事件交换机（topic 类型，持久化）
    public static final String EXCHANGE = "middleware.domain.events";

    // 笔记模块审核结果队列（audit.reviewed）
    public static final String NOTE_QUEUE = "middleware.note.events";
    // 媒体模块审核结果队列（audit.reviewed）
    public static final String MEDIA_QUEUE = "middleware.media.events";
    // 系统模块队列（storage.released 存储额度释放）
    public static final String SYSTEM_QUEUE = "middleware.system.events";
    // 系统模块邮件发送队列（email.send-requested）
    public static final String EMAIL_QUEUE = "middleware.system.email";
    // 媒体资源-图片删除异步任务队列（media.resource.delete-requested）
    public static final String MEDIA_DELETE_QUEUE = "middleware.media.resource-delete";
    // 审核模块用户资料投影队列（user.profile-changed）
    public static final String AUDIT_PROJECTION_QUEUE = "middleware.audit.projections";

    private EventTopology() {
    }

    /** 重试队列命名：<主队列>.retry（TTL 到期后重新投递回主队列） */
    public static String retryQueue(String queue) {
        return queue + ".retry";
    }

    /** 死信队列命名：<主队列>.dlq（重试耗尽后落死信，人工排查） */
    public static String deadLetterQueue(String queue) {
        return queue + ".dlq";
    }
}
