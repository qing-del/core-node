package com.jacolp.document.websocket.protocol;

import java.util.Arrays;
import java.util.Objects;

/** LINK 帧外层 Envelope 与不透明 raw Yjs update 的拆包结果。 */
public record DocumentLinkPayload(
        /** 已通过协议校验的绑定意图。 */
        DocumentBindingEnvelope envelope,
        /** 不由 Java 解析的 raw Yjs update。 */
        byte[] rawYjsUpdate) {

    public DocumentLinkPayload {
        Objects.requireNonNull(envelope, "envelope must not be null");
        if (rawYjsUpdate == null || rawYjsUpdate.length == 0) {
            throw new DocumentWsProtocolException("document LINK raw Yjs update must not be empty");
        }
        rawYjsUpdate = Arrays.copyOf(rawYjsUpdate, rawYjsUpdate.length);
    }

    /** 返回 raw Yjs update 的副本，防止协议对象被调用方修改。 */
    @Override
    public byte[] rawYjsUpdate() {
        return Arrays.copyOf(rawYjsUpdate, rawYjsUpdate.length);
    }
}
