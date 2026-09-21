package com.jacolp.document.application.fileindex;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** file Elasticsearch 投影的完整 source 模型。 */
public record FileIndexDocument(
        String fileName,
        FileIndexResourceType resourceType,
        String resourceId,
        String resourceUrl,
        List<String> visibleUserIds,
        boolean isPublic,
        boolean isDelete) {

    /** 固化可见用户顺序、去除重复值，并校验设计文档规定的字段语义。 */
    public FileIndexDocument {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        Objects.requireNonNull(visibleUserIds, "visibleUserIds must not be null");
        LinkedHashSet<String> uniqueUserIds = new LinkedHashSet<>();
        for (String userId : visibleUserIds) {
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException("visibleUserIds must not contain blank values");
            }
            uniqueUserIds.add(userId);
        }
        if (uniqueUserIds.isEmpty()) {
            throw new IllegalArgumentException("visibleUserIds must not be empty");
        }
        visibleUserIds = List.copyOf(uniqueUserIds);

        if (resourceType == FileIndexResourceType.IMAGE) {
            if (resourceUrl == null || resourceUrl.isBlank()) {
                throw new IllegalArgumentException("IMAGE resourceUrl must not be blank");
            }
        } else if (resourceUrl != null && !resourceUrl.isBlank()) {
            throw new IllegalArgumentException("only IMAGE may set resourceUrl");
        } else {
            resourceUrl = null;
        }
    }
}
