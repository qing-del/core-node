package com.jacolp.document.application.access;

import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import java.util.Objects;

/** 当前用户对一篇活跃文档的有效访问结果。 */
public record DocumentAccess(DocumentDO document, DocumentPermission permission, boolean owner) {

    /**
     * 创建访问结果；所有者使用 WRITE 作为有效读写权限，同时由 owner 标记区分管理权限。
     */
    public DocumentAccess {
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(permission, "permission must not be null");
        if (document.getId() == null || document.getId() <= 0) {
            throw new IllegalArgumentException("document id must be positive");
        }
    }

    /** READ、WRITE 和 OWNER 均可读取文档。 */
    public boolean canRead() {
        return true;
    }

    /** 所有者或 WRITE 授权用户可以提交文档更新。 */
    public boolean canWrite() {
        return owner || permission.canWrite();
    }
}
