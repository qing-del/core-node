package com.jacolp.document.application.admin;

import com.jacolp.document.controller.AdminDocumentTreeItem;
import com.jacolp.document.controller.AdminDocumentTreeNode;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.system.api.UserProfileApi;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 为管理员提供按文档所有者分组的协作文档目录。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class AdminDocumentService {

    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Shanghai");

    private final DocumentMapper documentMapper;
    private final UserProfileApi userProfileApi;

    public AdminDocumentService(DocumentMapper documentMapper, UserProfileApi userProfileApi) {
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper must not be null");
        this.userProfileApi = Objects.requireNonNull(userProfileApi, "userProfileApi must not be null");
    }

    /** 返回仅含正常文档的稳定用户根目录树。 */
    public List<AdminDocumentTreeNode> listTree() {
        List<DocumentDO> documents = documentMapper.listActiveForAdmin();
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        Map<Long, List<DocumentDO>> documentsByOwner = new LinkedHashMap<>();
        for (DocumentDO document : documents) {
            Long ownerUserId = document.getOwnerUserId();
            if (ownerUserId == null || ownerUserId <= 0 || document.getId() == null
                    || document.getLastModifyTime() == null) {
                throw new IllegalStateException("active document contains invalid administrator tree data");
            }
            documentsByOwner.computeIfAbsent(ownerUserId, ignored -> new ArrayList<>()).add(document);
        }
        Map<Long, UserProfileApi.UserProfile> profiles = userProfileApi.getProfilesByIds(documentsByOwner.keySet());
        return documentsByOwner.entrySet().stream().map(entry -> {
            UserProfileApi.UserProfile profile = profiles.get(entry.getKey());
            List<AdminDocumentTreeItem> items = entry.getValue().stream()
                    .map(AdminDocumentService::toItem)
                    .toList();
            return new AdminDocumentTreeNode(entry.getKey(), profile == null ? null : profile.username(),
                    profile == null ? null : profile.nickname(), items);
        }).toList();
    }

    private static AdminDocumentTreeItem toItem(DocumentDO document) {
        return new AdminDocumentTreeItem(document.getId(), document.getTitle(),
                document.getLastModifyTime().atZone(APPLICATION_ZONE).toInstant().toEpochMilli(),
                document.getLastModifyUserId(), document.getContentObjectKey() != null
                && !document.getContentObjectKey().isBlank());
    }
}
