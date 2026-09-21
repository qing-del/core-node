package com.jacolp.document.application.access;

import com.jacolp.common.core.exception.PermissionDeniedException;

/** 文档资源不存在或当前用户没有对应 ACL 权限时抛出。 */
public class DocumentAccessDeniedException extends PermissionDeniedException {

    public enum Reason {
        NOT_FOUND,
        FORBIDDEN
    }

    private final Reason reason;

    private DocumentAccessDeniedException(Reason reason) {
        super("文档不存在或无权访问");
        this.reason = reason;
    }

    public static DocumentAccessDeniedException notFound() {
        return new DocumentAccessDeniedException(Reason.NOT_FOUND);
    }

    public static DocumentAccessDeniedException forbidden() {
        return new DocumentAccessDeniedException(Reason.FORBIDDEN);
    }

    public Reason reason() {
        return reason;
    }
}
