package com.jacolp.document.application.yjs;

import java.util.Objects;

/** Yjs 节点身份迁移操作返回的完整状态和变更摘要。 */
public record YjsNodeIdentityMigrationResult(byte[] mergedState, boolean changed, int registeredNodeCount) {

    /** 校验迁移服务返回了可保存的状态和非负节点数量。 */
    public YjsNodeIdentityMigrationResult {
        Objects.requireNonNull(mergedState, "mergedState must not be null");
        if (mergedState.length == 0) {
            throw new IllegalArgumentException("mergedState must not be empty");
        }
        if (registeredNodeCount < 0) {
            throw new IllegalArgumentException("registeredNodeCount must not be negative");
        }
    }
}
