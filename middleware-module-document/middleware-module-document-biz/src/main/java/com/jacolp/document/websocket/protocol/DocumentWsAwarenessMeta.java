package com.jacolp.document.websocket.protocol;

import java.util.Objects;
import java.util.UUID;

/**
 * 服务端发布的 Awareness Session 元数据控制帧。
 *
 * <p>该消息只描述可信的 Session 身份，不携带或解析 Yjs Awareness 二进制 payload。</p>
 */
public record DocumentWsAwarenessMeta(
        /** 控制帧协议版本。 */
        int protocolVersion,
        /** 固定为 {@link DocumentWsControlType#AWARENESS_META}。 */
        DocumentWsControlType type,
        /** 服务端为本次元数据事件生成的关联 ID。 */
        UUID requestId,
        /** 元数据新增/更新或移除动作。 */
        DocumentWsAwarenessAction action,
        /** 元数据所属文档 ID。 */
        long documentId,
        /** Yjs Awareness client ID。 */
        long awarenessClientId,
        /** 服务端 WebSocket Session ID。 */
        String sessionId,
        /** 认证用户 ID；REMOVE 时可以为空。 */
        Long userId,
        /** 认证主体用户名；REMOVE 时可以为空。 */
        String name,
        /** 服务端分配的光标颜色；REMOVE 时可以为空。 */
        String color) {

    /** 校验元数据控制帧的固定类型、身份主键和 UPSERT 所需展示字段。 */
    public DocumentWsAwarenessMeta {
        Objects.requireNonNull(type, "type must not be null");
        if (type != DocumentWsControlType.AWARENESS_META) {
            throw new IllegalArgumentException("awareness metadata type must be AWARENESS_META");
        }
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        if (documentId <= 0 || awarenessClientId <= 0) {
            throw new IllegalArgumentException("documentId and awarenessClientId must be positive");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (action == DocumentWsAwarenessAction.UPSERT) {
            if (userId == null || userId <= 0) {
                throw new IllegalArgumentException("userId must be positive for UPSERT");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank for UPSERT");
            }
            if (color == null || color.isBlank()) {
                throw new IllegalArgumentException("color must not be blank for UPSERT");
            }
        }
    }
}
