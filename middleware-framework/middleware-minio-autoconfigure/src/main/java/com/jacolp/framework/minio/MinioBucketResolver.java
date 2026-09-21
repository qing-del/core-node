package com.jacolp.framework.minio;

/** Resolves a module-neutral logical bucket name from {@code jacolp.minio.bucket.*}. */
public interface MinioBucketResolver {

    /**
     * Returns the configured physical bucket name for a logical name.
     *
     * @throws MinioStorageException if the logical bucket name is blank or has not been configured
     */
    String requireBucket(String logicalBucketName);
}
