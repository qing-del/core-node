package com.jacolp.document.application.migration;

import com.jacolp.document.application.yjs.YjsResourceReferenceRemap;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentRelationMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 原子切换迁移快照指针，并为重复资源引用复制活动关系。 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentNodeIdentityMigrationPersistence {

    private final DocumentMapper documentMapper;
    private final DocumentRelationMapper documentRelationMapper;

    public DocumentNodeIdentityMigrationPersistence(DocumentMapper documentMapper,
                                                    DocumentRelationMapper documentRelationMapper) {
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper must not be null");
        this.documentRelationMapper = Objects.requireNonNull(documentRelationMapper,
                "documentRelationMapper must not be null");
    }

    /** CAS 成功后复制每条关系；任一复制失败会回滚已更新的快照指针。 */
    @Transactional
    public boolean replaceSnapshotAndCloneRelations(long documentId, long expectedPersistedLogId, String objectKey,
                                                    long cutoffLogId,
                                                    List<YjsResourceReferenceRemap> resourceReferenceRemaps) {
        int affected = documentMapper.updateSnapshotPointerIfPersistedLogId(documentId, expectedPersistedLogId,
                objectKey, cutoffLogId);
        if (affected != 1) {
            return false;
        }
        for (YjsResourceReferenceRemap remap : resourceReferenceRemaps) {
            int cloned = documentRelationMapper.cloneActiveBySourceDocumentIdAndRefId(documentId,
                    remap.previousRefId(), remap.refId());
            if (cloned != 1) {
                throw new DocumentNodeIdentityMigrationException("could not clone active document relation for "
                        + "resource reference remap " + remap.previousRefId());
            }
        }
        return true;
    }
}
