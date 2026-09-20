package com.jacolp.framework.minio;

import java.util.List;

/**
 * Common byte-oriented MinIO operations for modules that do not need direct SDK access.
 *
 * <p>The caller owns key naming, retention, and business metadata. This API deliberately does
 * not create buckets or infer a business bucket from an object key.</p>
 */
public interface MinioObjectStorage {

    /** 读取对象，并拒绝超过 {@code maxBytes} 的响应。 */
    byte[] read(String bucket, String objectKey, long maxBytes);

    /** 按调用方提供的精确键存储对象；桶不存在时按需创建。 */
    void write(String bucket, String objectKey, byte[] content, String contentType);

    /** 枚举指定前缀下的对象键和精确对象大小，不下载对象内容。 */
    List<MinioObjectSummary> listByPrefix(String bucket, String prefix);

    /** 删除调用方提供的精确对象键。 */
    void delete(String bucket, String objectKey);
}
