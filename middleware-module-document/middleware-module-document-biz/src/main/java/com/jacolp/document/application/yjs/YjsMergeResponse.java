package com.jacolp.document.application.yjs;

import java.util.Base64;

/** {@code POST /internal/yjs/merge} 返回的 JSON 响应报文。 */
record YjsMergeResponse(
        /** 合并后的完整 Yjs 状态，使用 Base64 编码返回。<p>example: {@code AAECAwQ=}</p> */
        String mergedState) {

    /** 解码合并服务返回的 Base64 状态，并把格式错误转换为领域异常。 */
    byte[] decodeMergedState() {
        if (mergedState == null || mergedState.isBlank()) {
            throw new YjsMergeException("Yjs merge response is missing mergedState");
        }
        try {
            return Base64.getDecoder().decode(mergedState);
        } catch (IllegalArgumentException exception) {
            throw new YjsMergeException("Yjs merge response contains invalid Base64", exception);
        }
    }
}
