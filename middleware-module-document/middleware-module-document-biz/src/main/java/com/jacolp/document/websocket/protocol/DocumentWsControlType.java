package com.jacolp.document.websocket.protocol;

/** v0.3 文本帧控制协议中的控制类型。 */
public enum DocumentWsControlType {
    JOIN_DOCUMENT,
    JOIN_ACCEPTED,
    SYNC_COMPLETE,
    LEAVE_DOCUMENT,
    UPDATE_ACCEPTED,
    LINK_ACCEPTED,
    AWARENESS_META,
    ERROR,
    PING,
    PONG
}
