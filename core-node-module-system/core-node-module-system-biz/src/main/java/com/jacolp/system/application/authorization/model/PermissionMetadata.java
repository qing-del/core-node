package com.jacolp.system.application.authorization.model;

import java.time.LocalDateTime;

/**
 * Application-facing permission catalogue metadata.
 */
public record PermissionMetadata(
        Long id,
        String code,
        String oauthScope,
        String resource,
        String action,
        String status,
        String description,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
