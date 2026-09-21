package com.jacolp.document.websocket.exception;

/** Room 将超过配置的并发会话上限时抛出。 */
public class DocumentRoomLimitExceededException extends RuntimeException {

    /** 创建文档 Room 容量超限异常。 */
    public DocumentRoomLimitExceededException(String message) {
        super(message);
    }
}
