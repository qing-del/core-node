package com.jacolp.document.application.access;

import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentUserMappingDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentUserMappingMapper;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 统一计算文档所有者和直接用户授权的有效权限。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentAccessService {

    private final DocumentMapper documentMapper;
    private final DocumentUserMappingMapper mappingMapper;

    public DocumentAccessService(DocumentMapper documentMapper, DocumentUserMappingMapper mappingMapper) {
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper must not be null");
        this.mappingMapper = Objects.requireNonNull(mappingMapper, "mappingMapper must not be null");
    }

    /** 要求调用方至少拥有文档读取权限。 */
    public DocumentAccess requireRead(long documentId, long userId) {
        requirePositive(documentId, "documentId");
        requirePositive(userId, "userId");

        DocumentDO document = documentMapper.selectActiveById(documentId);
        if (document == null) {
            throw DocumentAccessDeniedException.notFound();
        }
        if (Objects.equals(document.getOwnerUserId(), userId)) {
            return new DocumentAccess(document, DocumentPermission.WRITE, true);
        }

        DocumentUserMappingDO mapping = mappingMapper.selectEnabledByDocumentIdAndUserId(documentId, userId);
        if (mapping == null
                || !Boolean.TRUE.equals(mapping.getEnabled())
                || !Objects.equals(mapping.getDocumentId(), documentId)
                || !Objects.equals(mapping.getUserId(), userId)
                || mapping.getPermission() == null) {
            throw DocumentAccessDeniedException.forbidden();
        }
        return new DocumentAccess(document, mapping.getPermission(), false);
    }

    /** 要求调用方拥有提交 CRDT 更新的权限。 */
    public DocumentAccess requireWrite(long documentId, long userId) {
        DocumentAccess access = requireRead(documentId, userId);
        if (!access.canWrite()) {
            throw DocumentAccessDeniedException.forbidden();
        }
        return access;
    }

    /** 要求调用方是文档所有者。 */
    public DocumentAccess requireOwner(long documentId, long userId) {
        DocumentAccess access = requireRead(documentId, userId);
        if (!access.owner()) {
            throw DocumentAccessDeniedException.forbidden();
        }
        return access;
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
