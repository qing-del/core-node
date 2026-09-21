package com.jacolp.document.websocket.protocol;

import java.util.Arrays;

/** 二进制外层帧类型；其 payload 为原始 Yjs 或 awareness 数据。 */
public enum DocumentWsFrameType {
    CLIENT_UPDATE(0x01),
    CRDT_UPDATE(0x02),
    SNAPSHOT_STATE(0x03),
    BOOTSTRAP_UPDATE(0x04),
    AWARENESS(0x05),
    LINK(0x06);

    private final int wireValue;

    /** 绑定协议中使用的单字节帧类型值。 */
    DocumentWsFrameType(int wireValue) {
        this.wireValue = wireValue;
    }

    /** 返回该帧类型在二进制协议中的数值。 */
    public int wireValue() {
        return wireValue;
    }

    /** 将线上的数值解析为受支持的帧类型，未知值直接拒绝。 */
    public static DocumentWsFrameType fromWireValue(int wireValue) {
        return Arrays.stream(values())
                .filter(type -> type.wireValue == wireValue)
                .findFirst()
                .orElseThrow(() -> new DocumentWsProtocolException("unknown document WebSocket frame type: " + wireValue));
    }
}
