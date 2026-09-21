package com.jacolp.document.application.admin;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.document.application.compact.DocumentSnapshotStorage;
import com.jacolp.document.controller.AdminSnapshotHistoryClearItem;
import com.jacolp.document.controller.AdminSnapshotHistoryClearResponse;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.framework.minio.MinioObjectSummary;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 批量回收不被当前文档快照指针引用的 MinIO 历史对象。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class AdminDocumentSnapshotHistoryService {

    private static final Logger log = LoggerFactory.getLogger(AdminDocumentSnapshotHistoryService.class);

    private final DocumentMapper documentMapper;
    private final DocumentSnapshotStorage snapshotStorage;

    public AdminDocumentSnapshotHistoryService(DocumentMapper documentMapper,
                                               DocumentSnapshotStorage snapshotStorage) {
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper must not be null");
        this.snapshotStorage = Objects.requireNonNull(snapshotStorage, "snapshotStorage must not be null");
    }

    /**
     * 先验证整批文档均正常存在，再逐篇删除历史快照；MinIO 不支持跨对象事务，失败项返回可重试结果。
     */
    public AdminSnapshotHistoryClearResponse clear(List<Long> requestedDocumentIds) {
        List<Long> documentIds = normalizeIds(requestedDocumentIds);
        validateActiveDocuments(documentIds);

        List<AdminSnapshotHistoryClearItem> outcomes = documentIds.stream()
                .map(this::clearOne)
                .toList();
        long deletedObjectCount = outcomes.stream().mapToLong(AdminSnapshotHistoryClearItem::deletedObjectCount).sum();
        long releasedBytes = outcomes.stream().mapToLong(AdminSnapshotHistoryClearItem::releasedBytes).sum();
        return new AdminSnapshotHistoryClearResponse(outcomes, deletedObjectCount, releasedBytes);
    }

    private void validateActiveDocuments(List<Long> documentIds) {
        List<DocumentDO> documents = documentMapper.selectActiveByIds(documentIds);
        Map<Long, DocumentDO> documentsById = (documents == null ? List.<DocumentDO>of() : documents).stream()
                .filter(document -> document.getId() != null)
                .collect(Collectors.toMap(DocumentDO::getId, Function.identity()));
        if (documentsById.size() != documentIds.size()
                || documentIds.stream().anyMatch(id -> !documentsById.containsKey(id))) {
            throw new BaseException("文档不存在或已删除");
        }
    }

    private AdminSnapshotHistoryClearItem clearOne(long documentId) {
        // 重新读取确保当前指针在真正列举对象时仍被排除；快照指针只会切换到新对象，旧对象不会重新成为当前对象。
        DocumentDO currentDocument = documentMapper.selectActiveById(documentId);
        if (currentDocument == null) {
            return failed(documentId, 0, 0, "文档已删除，未执行清理");
        }
        long deletedCount = 0;
        long releasedBytes = 0;
        try {
            List<MinioObjectSummary> historicalSnapshots = snapshotStorage.listHistorical(documentId,
                    currentDocument.getContentObjectKey());
            for (MinioObjectSummary snapshot : historicalSnapshots) {
                try {
                    snapshotStorage.delete(snapshot.objectKey());
                    deletedCount++;
                    releasedBytes = Math.addExact(releasedBytes, snapshot.size());
                } catch (RuntimeException exception) {
                    log.warn("Could not delete historical document snapshot, documentId={}, objectKey={}",
                            documentId, snapshot.objectKey(), exception);
                    return failed(documentId, deletedCount, releasedBytes, "删除快照失败，可重试");
                }
            }
            return new AdminSnapshotHistoryClearItem(documentId, deletedCount, releasedBytes, true, null);
        } catch (RuntimeException exception) {
            log.warn("Could not enumerate historical document snapshots, documentId={}", documentId, exception);
            return failed(documentId, deletedCount, releasedBytes, "读取历史快照失败，可重试");
        }
    }

    private static List<Long> normalizeIds(List<Long> requestedDocumentIds) {
        if (requestedDocumentIds == null || requestedDocumentIds.isEmpty()) {
            throw new BaseException("文档 ID 列表不能为空");
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long id : requestedDocumentIds) {
            if (id == null || id <= 0) {
                throw new BaseException("文档 ID 必须为正整数");
            }
            ids.add(id);
        }
        return List.copyOf(ids);
    }

    private static AdminSnapshotHistoryClearItem failed(long documentId, long deletedCount, long releasedBytes,
                                                         String failureMessage) {
        return new AdminSnapshotHistoryClearItem(documentId, deletedCount, releasedBytes, false, failureMessage);
    }
}
