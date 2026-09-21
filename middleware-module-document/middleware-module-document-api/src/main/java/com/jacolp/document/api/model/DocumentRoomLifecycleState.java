package com.jacolp.document.api.model;

/** 协作文档 Room 的仅运行时生命周期状态。 */
public enum DocumentRoomLifecycleState {
    OPEN,
    ACTIVE,
    PRE_CLOSE,
    CLOSING,
    CLOSED
}
