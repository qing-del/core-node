package com.jacolp.document.api.model;

import java.util.Objects;

/** 可跨模块传递的最小文档元数据；不包含 CRDT 正文和对象存储指针。 */
public record DocumentMetadata(
        /** 文档数据库主键。<p>example: {@code 42}</p> */
        long documentId,
        /** 文档所有者用户 ID。<p>example: {@code 10001}</p> */
        long ownerUserId,
        /** 文档标题，不包含 CRDT 正文。<p>example: {@code 项目设计文档}</p> */
        String title,
        /** 最近一次接受更新的时间戳，单位为 Unix 毫秒。<p>example: {@code 1756080000000}</p> */
        long lastModifyTime,
        /** 最近修改文档的用户 ID；尚无修改者时为空。<p>example: {@code 10001}</p> */
        Long lastModifyUserId,
        /** 文档是否已逻辑删除。<p>example: {@code false}</p> */
        boolean deleted,
        /** 当前调用方对文档的有效权限；只返回 READ 或 WRITE。 */
        String permission,
        /** 当前调用方是否为文档所有者。 */
        boolean owner
) {
    /** 兼容只包含基础字段的旧调用方；新接口应使用带权限的构造方法。 */
    public DocumentMetadata(long documentId, long ownerUserId, String title, long lastModifyTime,
                            Long lastModifyUserId, boolean deleted) {
        this(documentId, ownerUserId, title, lastModifyTime, lastModifyUserId, deleted, "WRITE", true);
    }

    /** 校验元数据的范围和标题约束，避免无效状态跨模块传播。 */
    public DocumentMetadata {
        if (documentId <= 0) {
            // 文档 ID 会作为数据库、Redis 和 WebSocket Room 的共同 key，非正数没有有效业务含义。
            throw new IllegalArgumentException("documentId must be positive");
        }
        if (ownerUserId <= 0) {
            // 所有者 ID 是后续 ACL 判定的身份边界，不能让缺失或占位值跨模块传播。
            throw new IllegalArgumentException("ownerUserId must be positive");
        }
        title = Objects.requireNonNull(title, "title").trim();
        if (title.isEmpty()) {
            // 标题先去除首尾空白，再拒绝空标题，保证列表和编辑页展示一致。
            throw new IllegalArgumentException("title must not be blank");
        }
        if (lastModifyTime < 0) {
            // 时间戳向前端传递为 Unix 毫秒，负值通常表示错误的持久化数据。
            throw new IllegalArgumentException("lastModifyTime must not be negative");
        }
        if (!"READ".equals(permission) && !"WRITE".equals(permission)) {
            // 未知权限不能默认按 WRITE 处理，避免错误元数据意外开放编辑能力。
            throw new IllegalArgumentException("permission must be READ or WRITE");
        }
    }
}
