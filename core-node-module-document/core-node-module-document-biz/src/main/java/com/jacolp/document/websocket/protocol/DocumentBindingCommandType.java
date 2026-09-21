package com.jacolp.document.websocket.protocol;

import java.util.Arrays;

/** LINK BindingEnvelope 中的绑定命令类型。 */
public enum DocumentBindingCommandType {
    BIND(0x01),
    UNBIND(0x02);

    private final int wireValue;

    DocumentBindingCommandType(int wireValue) {
        this.wireValue = wireValue;
    }

    /** 返回线上使用的单字节命令值。 */
    public int wireValue() {
        return wireValue;
    }

    /** 将线上命令值解析为受支持的 Binding 命令。 */
    public static DocumentBindingCommandType fromWireValue(int wireValue) {
        return Arrays.stream(values())
                .filter(type -> type.wireValue == wireValue)
                .findFirst()
                .orElseThrow(() -> new DocumentWsProtocolException(
                        "unknown document LINK binding command type: " + wireValue));
    }
}
