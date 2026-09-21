package com.jacolp.document.websocket.protocol;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** 二进制帧外层结构；其中 payload 对 Java 始终是未解析的 Yjs 字节。 */
public record DocumentWsBinaryFrame(
        /** 二进制帧的业务类型。<p>example: {@code CLIENT_UPDATE}</p> */
        DocumentWsFrameType type,
        /** 用于关联客户端发送和服务端广播的事件 UUID。<p>example: {@code 550e8400-e29b-41d4-a716-446655440000}</p> */
        UUID eventId,
        /** 未解析的 Yjs 或 awareness 二进制负载。<p>example: {@code [0x01, 0x02, 0x7f]}</p> */
        byte[] payload) {

    /** 校验帧类型和事件 ID，并复制 payload 以保持协议对象不可变。 */
    public DocumentWsBinaryFrame {
        type = Objects.requireNonNull(type, "type must not be null");
        eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
    }

    /** 返回 payload 的副本，防止调用方修改已编码帧。 */
    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
