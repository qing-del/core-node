package com.jacolp.document.application.fileindex;

import java.util.Objects;

/** 一个 file 投影资源的稳定定位键。 */
public record FileIndexResourceKey(FileIndexResourceType resourceType, long resourceId) {

    /** 校验资源类型和正数主键，避免生成无法重放的投影 ID。 */
    public FileIndexResourceKey {
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        if (resourceId <= 0) {
            throw new IllegalArgumentException("resourceId must be positive");
        }
    }

    /** 返回跨来源统一的 Elasticsearch 文档 ID，例如 {@code DOCUMENT:42}。 */
    public String indexId() {
        return resourceType.name() + ":" + resourceId;
    }
}
