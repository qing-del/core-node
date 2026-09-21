package com.jacolp.document.websocket.protocol;

import java.util.Objects;
import java.util.UUID;

/** LINK 双 Stream 原子入队成功后的文本确认消息。 */
public record DocumentWsLinkAcceptedMessage(
        /** 与既有 WebSocket 控制帧保持一致的协议版本。 */
        int protocolVersion,
        /** 固定为 LINK_ACCEPTED。 */
        DocumentWsControlType type,
        /** 关联原始 LINK 帧的事件 ID。 */
        UUID requestId,
        /** 发生绑定动作的源文档 ID。 */
        long documentId,
        /** 原始 LINK 帧外层携带的客户端更新 ID。 */
        UUID clientUpdateId,
        /** updates Stream 的入队 ID。 */
        String updatesRedisOpId,
        /** binding Stream 的入队 ID。 */
        String bindingRedisOpId,
        /** v0.6 固定为 QUEUED。 */
        String status) {

    public DocumentWsLinkAcceptedMessage {
        if (type != DocumentWsControlType.LINK_ACCEPTED) {
            throw new DocumentWsProtocolException("LINK accepted message type must be LINK_ACCEPTED");
        }
        if (requestId == null || clientUpdateId == null) {
            throw new DocumentWsProtocolException("LINK accepted message IDs are required");
        }
        if (documentId <= 0) {
            throw new DocumentWsProtocolException("LINK accepted documentId must be positive");
        }
        if (updatesRedisOpId == null || updatesRedisOpId.isBlank()
                || bindingRedisOpId == null || bindingRedisOpId.isBlank()) {
            throw new DocumentWsProtocolException("LINK accepted Redis operation IDs are required");
        }
        if (!Objects.equals(status, "QUEUED")) {
            throw new DocumentWsProtocolException("LINK accepted status must be QUEUED");
        }
    }
}
