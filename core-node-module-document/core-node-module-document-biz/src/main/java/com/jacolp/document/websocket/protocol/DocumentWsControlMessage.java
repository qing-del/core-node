package com.jacolp.document.websocket.protocol;

import java.util.UUID;

/** JSON 控制帧；某个控制类型不需要的字段保持为 null。 */
public record DocumentWsControlMessage(
        /** 控制帧协议版本。<p>example: {@code 1}</p> */
        int protocolVersion,
        /** 控制消息类型。<p>example: {@code JOIN_DOCUMENT}</p> */
        DocumentWsControlType type,
        /** 请求关联 ID；服务端 ACK/错误消息使用它回关联客户端请求。<p>example: {@code 550e8400-e29b-41d4-a716-446655440000}</p> */
        UUID requestId,
        /** 控制消息关联的文档 ID；全局错误时为空。<p>example: {@code 42}</p> */
        Long documentId,
        /** CLIENT_UPDATE 对应的客户端更新 UUID。<p>example: {@code 6ba7b810-9dad-11d1-80b4-00c04fd430c8}</p> */
        UUID clientUpdateId,
        /** Redis Stream 条目 ID；UPDATE_ACCEPTED 时用于确认服务端接收位置。<p>example: {@code 1756080000000-0}</p> */
        String redisOpId,
        /** 机器可判断的错误或状态编码。<p>example: {@code DOCUMENT_NOT_FOUND}</p> */
        String code,
        /** 面向客户端展示或日志记录的附加消息。<p>example: {@code 文档不存在}</p> */
        String message,
        /** JOIN_DOCUMENT 携带的 Yjs Awareness client ID；其他控制帧保持为空。 */
        Long awarenessClientId) {

    /** 保留既有控制帧构造方式；非 JOIN 消息没有 Awareness client ID。 */
    public DocumentWsControlMessage(int protocolVersion, DocumentWsControlType type, UUID requestId,
                                    Long documentId, UUID clientUpdateId, String redisOpId,
                                    String code, String message) {
        this(protocolVersion, type, requestId, documentId, clientUpdateId, redisOpId,
                code, message, null);
    }
}
