package com.jacolp.framework.minio;

/** MinIO 对象清单中的最小元数据，用于调用方安全计算待回收空间。 */
public record MinioObjectSummary(String objectKey, long size) {

    public MinioObjectSummary {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey must not be blank");
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
    }
}
