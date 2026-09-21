package com.jacolp.document.application.authorization;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.document.application.access.DocumentAccess;
import com.jacolp.document.application.access.DocumentAccessService;
import com.jacolp.document.controller.DocumentUserAuthorizationResponse;
import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentUserMappingDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentUserMappingMapper;
import com.jacolp.system.api.UserProfileApi;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理文档所有者授予的直接用户授权记录。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentUserAuthorizationService {

    private final DocumentAccessService accessService;
    private final DocumentUserMappingMapper mappingMapper;
    private final UserProfileApi userProfileApi;

    public DocumentUserAuthorizationService(DocumentAccessService accessService,
                                            DocumentUserMappingMapper mappingMapper,
                                            UserProfileApi userProfileApi) {
        this.accessService = Objects.requireNonNull(accessService, "accessService must not be null");
        this.mappingMapper = Objects.requireNonNull(mappingMapper, "mappingMapper must not be null");
        this.userProfileApi = Objects.requireNonNull(userProfileApi, "userProfileApi must not be null");
    }

    /** 只有文档所有者可以查看授权名单；返回结果包含已撤销记录。 */
    public List<DocumentUserAuthorizationResponse> list(long currentUserId, long documentId) {
        accessService.requireOwner(documentId, currentUserId);
        List<DocumentUserMappingDO> mappings = mappingMapper.selectByDocumentId(documentId);
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        return mappings.stream().map(DocumentUserAuthorizationService::toResponse).toList();
    }

    /** 新增或更新一条文档用户授权，联合主键决定是否覆盖原记录。 */
    @Transactional(rollbackFor = Exception.class)
    public DocumentUserAuthorizationResponse upsert(long currentUserId, long documentId, long userId,
                                                    DocumentPermission permission, Boolean enabled) {
        DocumentAccess access = accessService.requireOwner(documentId, currentUserId);
        requirePositive(userId, "userId");
        Objects.requireNonNull(permission, "permission must not be null");
        Objects.requireNonNull(enabled, "enabled must not be null");

        if (Objects.equals(access.document().getOwnerUserId(), userId)) {
            throw new BaseException("不能给文档所有者添加授权");
        }
        if (!userProfileApi.isActiveUser(userId)) {
            throw new BaseException("被授权用户不存在或未启用");
        }

        DocumentUserMappingDO mapping = new DocumentUserMappingDO(
                documentId, userId, permission, enabled, null, null);
        mappingMapper.upsertByDocumentOwner(mapping, currentUserId);

        // 重新检查文档状态，确保删除与授权写入并发时不会返回无效授权。
        accessService.requireOwner(documentId, currentUserId);
        DocumentUserMappingDO persisted = mappingMapper.selectByDocumentIdAndUserId(documentId, userId);
        if (persisted == null) {
            throw new BaseException("保存文档授权失败");
        }
        return toResponse(persisted);
    }

    /** 撤销授权但保留记录，重复撤销按幂等成功处理。 */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(long currentUserId, long documentId, long userId) {
        accessService.requireOwner(documentId, currentUserId);
        requirePositive(userId, "userId");
        mappingMapper.disableByDocumentOwner(documentId, userId, currentUserId);
    }

    private static DocumentUserAuthorizationResponse toResponse(DocumentUserMappingDO mapping) {
        if (mapping == null || mapping.getPermission() == null
                || mapping.getDocumentId() == null || mapping.getUserId() == null) {
            throw new BaseException("文档授权数据无效");
        }
        return new DocumentUserAuthorizationResponse(
                mapping.getDocumentId(), mapping.getUserId(), mapping.getPermission(),
                Boolean.TRUE.equals(mapping.getEnabled()), mapping.getCreateTime(), mapping.getUpdateTime());
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
