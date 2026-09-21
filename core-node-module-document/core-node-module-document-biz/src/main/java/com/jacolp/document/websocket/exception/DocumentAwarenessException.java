package com.jacolp.document.websocket.exception;

/** Awareness JOIN 或 Session 绑定失败时返回给客户端的结构化异常。 */
public final class DocumentAwarenessException extends RuntimeException {

    private final String code;

    /** 创建带稳定协议错误码的 Awareness 异常。 */
    public DocumentAwarenessException(String code, String message) {
        super(message);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        this.code = code;
    }

    /** 返回供 WebSocket ERROR 控制帧使用的稳定错误码。 */
    public String code() {
        return code;
    }

    /** 创建缺少 Awareness client ID 的 JOIN 错误。 */
    public static DocumentAwarenessException required() {
        return new DocumentAwarenessException("DOCUMENT_AWARENESS_CLIENT_ID_REQUIRED",
                "JOIN_DOCUMENT requires a positive awarenessClientId");
    }

    /** 创建 Awareness client ID 非法的 JOIN 错误。 */
    public static DocumentAwarenessException invalid() {
        return new DocumentAwarenessException("DOCUMENT_AWARENESS_CLIENT_ID_INVALID",
                "awarenessClientId must be positive");
    }

    /** 创建同一 WebSocket Session 重复绑定不同 client ID 的错误。 */
    public static DocumentAwarenessException mismatch() {
        return new DocumentAwarenessException("DOCUMENT_AWARENESS_CLIENT_ID_MISMATCH",
                "WebSocket session is already bound to another awarenessClientId");
    }

    /** 创建同一 Room 内不同 Session 重复使用 client ID 的错误。 */
    public static DocumentAwarenessException conflict() {
        return new DocumentAwarenessException("DOCUMENT_AWARENESS_CLIENT_ID_CONFLICT",
                "awarenessClientId is already active in this document Room");
    }
}
