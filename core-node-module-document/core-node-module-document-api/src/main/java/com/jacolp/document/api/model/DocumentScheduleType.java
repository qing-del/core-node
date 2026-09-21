package com.jacolp.document.api.model;

/** 调度消息类型；消息本身不携带文档正文或 Yjs 更新。 */
public enum DocumentScheduleType {
    FLUSH_LOG,
    COMPACT,
    CLOSE
}
