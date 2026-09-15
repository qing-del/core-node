package com.jacolp.document.application.yjs;

import java.util.List;
import java.util.Objects;

/** Yjs 节点身份迁移操作返回的完整状态和变更摘要。 */
public record YjsNodeIdentityMigrationResult(byte[] mergedState, boolean changed, int registeredNodeCount,
                                              List<YjsResourceReferenceRemap> resourceReferenceRemaps) {

    /** 校验迁移服务返回了可保存的状态和非负节点数量。 */
    public YjsNodeIdentityMigrationResult {
        Objects.requireNonNull(mergedState, "mergedState must not be null");
        if (mergedState.length == 0) {
            throw new IllegalArgumentException("mergedState must not be empty");
        }
        if (registeredNodeCount < 0) {
            throw new IllegalArgumentException("registeredNodeCount must not be negative");
        }
        resourceReferenceRemaps = List.copyOf(Objects.requireNonNull(resourceReferenceRemaps,
                "resourceReferenceRemaps must not be null"));
    }

    /** 兼容未返回重复引用重映射的既有调用方和测试。 */
    public YjsNodeIdentityMigrationResult(byte[] mergedState, boolean changed, int registeredNodeCount) {
        this(mergedState, changed, registeredNodeCount, List.of());
    }
}
