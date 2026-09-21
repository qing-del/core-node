package com.jacolp.framework.minio;

import java.util.Objects;

final class DefaultMinioBucketResolver implements MinioBucketResolver {

    private final MinioProperties properties;

    /** 保存配置对象；桶名解析延迟到业务实际请求时执行。 */
    DefaultMinioBucketResolver(MinioProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /** 将逻辑桶名解析为配置中的物理桶名。 */
    @Override
    public String requireBucket(String logicalBucketName) {
        // 先拒绝空逻辑名，避免访问错误的配置键。
        if (logicalBucketName == null || logicalBucketName.isBlank()) {
            throw new MinioStorageException("logical MinIO bucket name must not be blank");
        }
        // 组件不在启动阶段创建桶，也不为业务推断默认桶名。
        String bucket = properties.getBucket().get(logicalBucketName);
        if (bucket == null || bucket.isBlank()) {
            throw new MinioStorageException("jacolp.minio.bucket.%s must be configured".formatted(logicalBucketName));
        }
        return bucket;
    }
}
