package com.jacolp.document.websocket.exception;

/** 无法完整读取或发送文档 bootstrap 数据时抛出。 */
public class DocumentBootstrapException extends RuntimeException {

    /** 保留快照、日志读取或 WebSocket 发送的根因。 */
    public DocumentBootstrapException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 创建不带底层原因的 bootstrap 异常。 */
    public DocumentBootstrapException(String message) {
        super(message);
    }
}
