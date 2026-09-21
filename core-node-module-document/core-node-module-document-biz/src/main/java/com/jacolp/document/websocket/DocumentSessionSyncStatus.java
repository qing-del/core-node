package com.jacolp.document.websocket;

/** 新加入会话在 SYNCING 期间接收 bootstrap 与实时帧，完成后进入 ACTIVE。 */
public enum DocumentSessionSyncStatus {
    SYNCING,
    ACTIVE
}
