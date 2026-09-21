package com.jacolp.document.websocket.exception;

/** 会话尝试访问不属于其已认证个人空间的 Room 时抛出。 */
public class DocumentRoomAccessException extends RuntimeException {

    /** 创建文档 Room 权限或生命周期拒绝异常。 */
    public DocumentRoomAccessException(String message) {
        super(message);
    }
}
