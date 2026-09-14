package com.jacolp.document.application.yjs;

import java.util.List;

/** 将 Yjs 应用更新与编码工作委托给隔离的 TypeScript 合并服务。 */
public interface YjsMergeClient {

    /**
     * 使用官方 Yjs 实现应用可选的基础状态和有序更新。
     * Java 只传递不透明二进制数据，从不解析 Yjs 正文。
     */
    byte[] merge(byte[] baseState, List<byte[]> updates);

    /**
     * 合并状态并补齐 v0.7 注册节点的 nodeId/nodeVersion；Java 仍不解析 Yjs 正文。
     */
    YjsNodeIdentityMigrationResult migrateNodeIdentity(byte[] baseState, List<byte[]> updates);
}
