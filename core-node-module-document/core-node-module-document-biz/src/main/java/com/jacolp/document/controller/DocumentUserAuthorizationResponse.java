package com.jacolp.document.controller;

import com.jacolp.document.enums.DocumentPermission;
import java.time.LocalDateTime;
import java.util.Objects;

/** 对外返回的文档用户授权信息，不直接暴露持久化实体。 */
public record DocumentUserAuthorizationResponse(
        long documentId,
        long userId,
        DocumentPermission permission,
        boolean enabled,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {

    public DocumentUserAuthorizationResponse {
        if (documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Objects.requireNonNull(permission, "permission must not be null");
    }
}
