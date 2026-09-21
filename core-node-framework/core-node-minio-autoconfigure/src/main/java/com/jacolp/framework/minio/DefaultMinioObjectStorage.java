package com.jacolp.framework.minio;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.Result;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class DefaultMinioObjectStorage implements MinioObjectStorage {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final MinioClient minioClient;

    /** 保存共享 MinIO 客户端，后续读写复用其连接配置。 */
    DefaultMinioObjectStorage(MinioClient minioClient) {
        this.minioClient = Objects.requireNonNull(minioClient, "minioClient must not be null");
    }

    /** 读取对象并在内存中执行最大字节数保护。 */
    @Override
    public byte[] read(String bucket, String objectKey, long maxBytes) {
        validateLocation(bucket, objectKey);
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            return readBounded(stream, maxBytes);
        } catch (MinioStorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MinioStorageException("could not read MinIO object", exception);
        }
    }

    /** 确保桶存在后按调用方提供的精确键写入完整字节数组。 */
    @Override
    public void write(String bucket, String objectKey, byte[] content, String contentType) {
        validateLocation(bucket, objectKey);
        Objects.requireNonNull(content, "content must not be null");
        String resolvedContentType = contentType == null || contentType.isBlank()
                ? DEFAULT_CONTENT_TYPE : contentType;
        try (ByteArrayInputStream stream = new ByteArrayInputStream(content)) {
            ensureBucketExists(bucket);
            minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey)
                    .stream(stream, content.length, -1)
                    .contentType(resolvedContentType)
                    .build());
        } catch (Exception exception) {
            throw new MinioStorageException("could not write MinIO object", exception);
        }
    }

    /** 不读取对象正文地枚举一个调用方已限定好的对象前缀。 */
    @Override
    public List<MinioObjectSummary> listByPrefix(String bucket, String prefix) {
        validateLocation(bucket, prefix);
        try {
            List<MinioObjectSummary> summaries = new ArrayList<>();
            Iterable<Result<Item>> objects = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket).prefix(prefix).recursive(true).build());
            for (Result<Item> object : objects) {
                Item item = object.get();
                summaries.add(new MinioObjectSummary(item.objectName(), item.size()));
            }
            return List.copyOf(summaries);
        } catch (MinioStorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MinioStorageException("could not list MinIO objects", exception);
        }
    }

    /**
     * Provision the logical bucket lazily so a newly deployed environment does not need a
     * separate bucket-creation step before its first upload.
     *
     * <p>The create operation is intentionally idempotent across application instances: another
     * instance may create the bucket after {@code bucketExists} returns false.</p>
     */
    private void ensureBucketExists(String bucket) throws Exception {
        // 先检查可避免重复创建；并发实例同时创建时由下方幂等错误码收敛。
        if (minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            return;
        }
        try {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        } catch (ErrorResponseException exception) {
            String errorCode = exception.errorResponse().code();
            if (!"BucketAlreadyExists".equals(errorCode) && !"BucketAlreadyOwnedByYou".equals(errorCode)) {
                throw exception;
            }
        }
    }

    /** 删除调用方指定的对象，不改变其他对象或桶的生命周期。 */
    @Override
    public void delete(String bucket, String objectKey) {
        validateLocation(bucket, objectKey);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw new MinioStorageException("could not delete MinIO object", exception);
        }
    }

    /** 分块读取对象，并在追加下一块前拒绝超过上限的响应。 */
    private static byte[] readBounded(InputStream stream, long maxBytes) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                // 写入下一块之前先检查，超过配置的内存上限时调用方不会得到不完整对象。
                if (output.size() > maxBytes - read) {
                    throw new MinioStorageException("MinIO object exceeds configured maximum size");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    /** 校验桶和对象键均可用于一次明确的 SDK 请求。 */
    private static void validateLocation(String bucket, String objectKey) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket must not be blank");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey must not be blank");
        }
    }
}
