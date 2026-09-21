package com.jacolp.framework.minio;

/** Runtime exception used by the module-neutral MinIO storage API. */
public class MinioStorageException extends RuntimeException {

    /** 创建不带底层原因的对象存储异常。 */
    public MinioStorageException(String message) {
        super(message);
    }

    /** 保留 SDK 或 IO 异常作为根因。 */
    public MinioStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
