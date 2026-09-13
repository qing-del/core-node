package com.jacolp.document.websocket.protocol;

import java.util.Arrays;

/** LINK BindingEnvelope 中的目标资源类型；v0.6 只开放协作文档。 */
public enum DocumentBindingTargetType {
    DOCUMENT(0x01, "DOCUMENT");

    private final int wireValue;
    private final String resourceType;

    DocumentBindingTargetType(int wireValue, String resourceType) {
        this.wireValue = wireValue;
        this.resourceType = resourceType;
    }

    /** 返回线上使用的单字节目标类型值。 */
    public int wireValue() {
        return wireValue;
    }

    /** 返回关系投影中使用的资源类型名称。 */
    public String resourceType() {
        return resourceType;
    }

    /** 将线上目标类型值解析为受支持的目标类型。 */
    public static DocumentBindingTargetType fromWireValue(int wireValue) {
        return Arrays.stream(values())
                .filter(type -> type.wireValue == wireValue)
                .findFirst()
                .orElseThrow(() -> new DocumentWsProtocolException(
                        "unknown document LINK binding target type: " + wireValue));
    }
}
