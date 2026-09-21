package com.jacolp.document.websocket.protocol;

/** 会返回给客户端的文档 WebSocket 协议违规异常。 */
public class DocumentWsProtocolException extends RuntimeException {

    /** 创建不带底层原因的协议异常。 */
    public DocumentWsProtocolException(String message) {
        super(message);
    }

    /** 保留 JSON 或编解码根因，供错误响应和日志诊断使用。 */
    public DocumentWsProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
