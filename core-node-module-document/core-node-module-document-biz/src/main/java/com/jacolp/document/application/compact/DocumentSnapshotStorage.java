package com.jacolp.document.application.compact;

import com.jacolp.document.config.DocumentProperties;
import com.jacolp.framework.minio.MinioBucketResolver;
import com.jacolp.framework.minio.MinioObjectSummary;
import com.jacolp.framework.minio.MinioObjectStorage;
import com.jacolp.framework.minio.MinioStorageException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 只读写二进制内容的 MinIO 适配器，用于保存不可变 Yjs 快照。 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentSnapshotStorage {

    private static final Logger log = LoggerFactory.getLogger(DocumentSnapshotStorage.class);

    private final MinioObjectStorage minioObjectStorage;
    private final MinioBucketResolver minioBucketResolver;
    private final DocumentProperties documentProperties;

    /** 创建只处理文档快照二进制内容的对象存储适配器。 */
    public DocumentSnapshotStorage(MinioObjectStorage minioObjectStorage, MinioBucketResolver minioBucketResolver,
                                   DocumentProperties documentProperties) {
        this.minioObjectStorage = Objects.requireNonNull(minioObjectStorage, "minioObjectStorage must not be null");
        this.minioBucketResolver = Objects.requireNonNull(minioBucketResolver, "minioBucketResolver must not be null");
        this.documentProperties = Objects.requireNonNull(documentProperties, "documentProperties must not be null");
    }

    /** 读取现有快照；空对象键表示该文档尚未生成快照。 */
    public byte[] read(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            // 新文档没有快照时由合并服务从空状态开始，不能把空 key 交给对象存储客户端。
            return null;
        }
        try {
            return minioObjectStorage.read(bucket(), objectKey, documentProperties.getSnapshot().getMaxBytes());
        } catch (DocumentSnapshotStorageException exception) {
            throw exception;
        } catch (MinioStorageException exception) {
            throw new DocumentSnapshotStorageException("could not read document snapshot", exception);
        }
    }

    /** 写入一个新快照对象，并返回永不复用的对象键。 */
    public String write(long documentId, byte[] yjsState) {
        if (documentId <= 0) {
            // 文档 ID 会组成不可变对象路径，非法 ID 不能生成无法归属的快照对象。
            throw new IllegalArgumentException("documentId must be positive");
        }
        Objects.requireNonNull(yjsState, "yjsState must not be null");
        validateSnapshotSize(yjsState.length);
        // 每次写入都生成新对象键，确保 CAS 切换前旧读者仍能读取旧快照。
        String objectKey = "document/%d/state/%s.bin".formatted(documentId, UUID.randomUUID());
        try {
            minioObjectStorage.write(bucket(), objectKey, yjsState, "application/octet-stream");
            return objectKey;
        } catch (MinioStorageException exception) {
            throw new DocumentSnapshotStorageException("could not write document snapshot", exception);
        }
    }

    /** 删除一次 CAS 失败后未被任何文档指针引用的快照对象。 */
    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey must not be blank");
        }
        try {
            minioObjectStorage.delete(bucket(), objectKey);
        } catch (DocumentSnapshotStorageException exception) {
            throw exception;
        } catch (MinioStorageException exception) {
            throw new DocumentSnapshotStorageException("could not delete document snapshot", exception);
        }
    }

    /** 列出一篇文档不再被当前快照指针引用的历史或孤儿快照对象。 */
    public List<MinioObjectSummary> listHistorical(long documentId, String currentObjectKey) {
        if (documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
        String prefix = "document/%d/state/".formatted(documentId);
        try {
            return minioObjectStorage.listByPrefix(bucket(), prefix).stream()
                    // 对象存储适配层保持通用；文档模块在边界再次收紧可清理路径。
                    .filter(summary -> summary.objectKey().startsWith(prefix)
                            && summary.objectKey().endsWith(".bin"))
                    .filter(summary -> !summary.objectKey().equals(currentObjectKey))
                    .toList();
        } catch (DocumentSnapshotStorageException exception) {
            throw exception;
        } catch (MinioStorageException exception) {
            throw new DocumentSnapshotStorageException("could not list document snapshots", exception);
        }
    }

    /** 解析文档逻辑桶名，并把配置错误转换为文档快照领域异常。 */
    private String bucket() {
        try {
            return minioBucketResolver.requireBucket("document");
        } catch (MinioStorageException exception) {
            throw new DocumentSnapshotStorageException("jacolp.minio.bucket.document is required for document snapshots", exception);
        }
    }

    /** 执行硬上限校验，并对接近上限的快照输出运维告警。 */
    private void validateSnapshotSize(int bytes) {
        if (bytes > documentProperties.getSnapshot().getMaxBytes()) {
            // 超过硬上限时阻止写入，避免异常正文持续占用对象存储并拖垮后续 bootstrap。
            throw new DocumentSnapshotStorageException("document snapshot exceeds configured maximum size");
        }
        if (bytes > documentProperties.getSnapshot().getWarnBytes()) {
            // 处于告警区间仍允许保存，以保证正常编辑链路不中断，同时留下运维信号。
            log.warn("Document snapshot size {} exceeds warn threshold {}", bytes,
                    documentProperties.getSnapshot().getWarnBytes());
        }
    }
}
