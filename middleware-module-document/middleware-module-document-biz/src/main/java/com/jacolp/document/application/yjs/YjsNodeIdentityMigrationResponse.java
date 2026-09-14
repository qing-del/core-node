package com.jacolp.document.application.yjs;

import java.util.Base64;

/** {@code POST /internal/yjs/node-identity/migrate} 的 JSON 响应。 */
record YjsNodeIdentityMigrationResponse(String mergedState, boolean changed, int registeredNodeCount) {

    /** 解码迁移服务返回的 Base64 状态，并转换格式错误为领域异常。 */
    YjsNodeIdentityMigrationResult decode() {
        if (mergedState == null || mergedState.isBlank()) {
            throw new YjsMergeException("Yjs node identity migration response is missing mergedState");
        }
        try {
            return new YjsNodeIdentityMigrationResult(Base64.getDecoder().decode(mergedState), changed,
                    registeredNodeCount);
        } catch (IllegalArgumentException exception) {
            throw new YjsMergeException("Yjs node identity migration response contains invalid Base64", exception);
        }
    }
}
