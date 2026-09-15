package com.jacolp.system.application.authorization.model;

import java.time.LocalDateTime;

/**
 * Application-facing role catalogue metadata.
 */
public record RoleMetadata(
        Long id,
        String roleName,
        String roleCode,
        Integer rank,
        Integer dailyApiLimit,
        Long maxStorageBytes,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
