package com.jacolp.document.application.fileindex;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentUserMappingDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentUserMappingMapper;
import com.jacolp.framework.elasticsearch.ElasticsearchIndexResolver;
import com.jacolp.framework.elasticsearch.ElasticsearchOperationException;
import com.jacolp.framework.elasticsearch.ElasticsearchOperations;
import com.jacolp.media.api.MediaFileIndexApi;
import com.jacolp.media.api.model.MediaFileIndexSource;
import com.jacolp.note.api.NoteFileIndexApi;
import com.jacolp.note.api.model.NoteFileIndexSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 从当前 MySQL/API 事实重建 file 文档，并提供单资源刷新和可重复全量重建。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document.file-index", name = "enabled", havingValue = "true")
public class FileIndexProjectionService {

    /** 没有专门配置时使用的 ID 游标页大小。 */
    public static final int DEFAULT_PAGE_SIZE = 100;

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchIndexResolver indexResolver;
    private final NoteFileIndexApi noteFileIndexApi;
    private final MediaFileIndexApi mediaFileIndexApi;
    private final DocumentMapper documentMapper;
    private final DocumentUserMappingMapper documentUserMappingMapper;

    /** 保存跨模块最小事实 API、文档 persistence 和 ES 投影操作。 */
    public FileIndexProjectionService(ElasticsearchClient elasticsearchClient,
                                      ElasticsearchOperations elasticsearchOperations,
                                      ElasticsearchIndexResolver indexResolver,
                                      NoteFileIndexApi noteFileIndexApi,
                                      MediaFileIndexApi mediaFileIndexApi,
                                      DocumentMapper documentMapper,
                                      DocumentUserMappingMapper documentUserMappingMapper) {
        this.elasticsearchClient = Objects.requireNonNull(elasticsearchClient, "elasticsearchClient must not be null");
        this.elasticsearchOperations = Objects.requireNonNull(elasticsearchOperations,
                "elasticsearchOperations must not be null");
        this.indexResolver = Objects.requireNonNull(indexResolver, "indexResolver must not be null");
        this.noteFileIndexApi = Objects.requireNonNull(noteFileIndexApi, "noteFileIndexApi must not be null");
        this.mediaFileIndexApi = Objects.requireNonNull(mediaFileIndexApi, "mediaFileIndexApi must not be null");
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper must not be null");
        this.documentUserMappingMapper = Objects.requireNonNull(documentUserMappingMapper,
                "documentUserMappingMapper must not be null");
    }

    /** 重新读取一个资源的当前事实；资源硬删除或不存在时删除稳定 ID 对应的 ES 文档。 */
    public void refresh(FileIndexResourceKey resourceKey) {
        Objects.requireNonNull(resourceKey, "resourceKey must not be null");
        String indexName = indexResolver.requireIndex(FileIndexMappingInitializer.LOGICAL_INDEX_NAME);
        FileIndexDocument document = loadCurrent(resourceKey);
        if (document == null) {
            elasticsearchOperations.delete(indexName, resourceKey.indexId());
            return;
        }
        elasticsearchOperations.index(indexName, resourceKey.indexId(), document);
    }

    /** 清理旧投影后按 NOTE、IMAGE、DOCUMENT 的 ID 游标重新写入全部当前事实。 */
    public void rebuildAll() {
        String indexName = indexResolver.requireIndex(FileIndexMappingInitializer.LOGICAL_INDEX_NAME);
        clearProjection(indexName);
        rebuildNotes(indexName);
        rebuildImages(indexName);
        rebuildDocuments(indexName);
    }

    /** 读取当前资源，所有跨模块状态映射均在本模块的投影边界完成。 */
    private FileIndexDocument loadCurrent(FileIndexResourceKey resourceKey) {
        return switch (resourceKey.resourceType()) {
            case NOTE -> noteFileIndexApi.findById(resourceKey.resourceId()).map(FileIndexProjectionService::toNote)
                    .orElse(null);
            case IMAGE -> mediaFileIndexApi.findById(resourceKey.resourceId()).map(FileIndexProjectionService::toImage)
                    .orElse(null);
            case DOCUMENT -> {
                DocumentDO document = documentMapper.selectById(resourceKey.resourceId());
                yield document == null ? null : toDocument(document,
                        documentUserMappingMapper.selectEnabledReadableByDocumentIds(List.of(resourceKey.resourceId())));
            }
        };
    }

    /** 逐页重建 NOTE，保留软删除源记录以生成 isDelete=true。 */
    private void rebuildNotes(String indexName) {
        long afterId = 0L;
        while (true) {
            List<NoteFileIndexSource> page = nonNull(noteFileIndexApi.listAfterId(afterId, DEFAULT_PAGE_SIZE));
            for (NoteFileIndexSource source : page) {
                write(indexName, new FileIndexResourceKey(FileIndexResourceType.NOTE, source.id()), toNote(source));
            }
            if (page.size() < DEFAULT_PAGE_SIZE) {
                return;
            }
            afterId = lastNoteId(page);
        }
    }

    /** 逐页重建 IMAGE，保留审核删除记录但不重新写入硬删除源。 */
    private void rebuildImages(String indexName) {
        long afterId = 0L;
        while (true) {
            List<MediaFileIndexSource> page = nonNull(mediaFileIndexApi.listAfterId(afterId, DEFAULT_PAGE_SIZE));
            for (MediaFileIndexSource source : page) {
                write(indexName, new FileIndexResourceKey(FileIndexResourceType.IMAGE, source.id()), toImage(source));
            }
            if (page.size() < DEFAULT_PAGE_SIZE) {
                return;
            }
            afterId = lastImageId(page);
        }
    }

    /** 逐页读取 DOCUMENT，并用一次批量授权查询组装 owner 与直接 READ/WRITE 用户。 */
    private void rebuildDocuments(String indexName) {
        long afterId = 0L;
        while (true) {
            List<DocumentDO> page = nonNull(documentMapper.selectByIdAfter(afterId, DEFAULT_PAGE_SIZE));
            if (page.isEmpty()) {
                return;
            }
            List<Long> documentIds = page.stream().map(DocumentDO::getId).toList();
            Map<Long, List<DocumentUserMappingDO>> mappingsByDocument = groupMappings(
                    documentUserMappingMapper.selectEnabledReadableByDocumentIds(documentIds));
            for (DocumentDO document : page) {
                List<DocumentUserMappingDO> mappings = mappingsByDocument.getOrDefault(document.getId(), List.of());
                FileIndexResourceKey key = new FileIndexResourceKey(FileIndexResourceType.DOCUMENT, requirePositive(
                        document.getId(), "document.id"));
                write(indexName, key, toDocument(document, mappings));
            }
            if (page.size() < DEFAULT_PAGE_SIZE) {
                return;
            }
            afterId = requirePositive(page.get(page.size() - 1).getId(), "document.id");
        }
    }

    /** 将当前资源写入稳定 ID，避免全量重建和增量刷新产生不同的定位方式。 */
    private void write(String indexName, FileIndexResourceKey key, FileIndexDocument document) {
        elasticsearchOperations.index(indexName, key.indexId(), document);
    }

    /** 用 delete-by-query 清理旧资源，确保硬删除源不会留下历史脏文档。 */
    private void clearProjection(String indexName) {
        try {
            elasticsearchClient.deleteByQuery(request -> request.index(indexName).refresh(true)
                    .query(query -> query.matchAll(matchAll -> matchAll)));
        } catch (IOException exception) {
            throw new ElasticsearchOperationException("could not clear file Elasticsearch projection", exception);
        }
    }

    private static FileIndexDocument toNote(NoteFileIndexSource source) {
        return new FileIndexDocument(source.fileName(), FileIndexResourceType.NOTE, String.valueOf(source.id()), null,
                List.of(String.valueOf(source.ownerUserId())), source.publicResource(), source.deleted());
    }

    private static FileIndexDocument toImage(MediaFileIndexSource source) {
        return new FileIndexDocument(source.fileName(), FileIndexResourceType.IMAGE, String.valueOf(source.id()),
                source.resourceUrl(), List.of(String.valueOf(source.ownerUserId())), source.publicResource(),
                source.deleted());
    }

    private static FileIndexDocument toDocument(DocumentDO document, List<DocumentUserMappingDO> mappings) {
        long documentId = requirePositive(document.getId(), "document.id");
        long ownerUserId = requirePositive(document.getOwnerUserId(), "document.ownerUserId");
        TreeSet<String> visibleUserIds = new TreeSet<>();
        visibleUserIds.add(String.valueOf(ownerUserId));
        if (mappings != null) {
            for (DocumentUserMappingDO mapping : mappings) {
                if (mapping == null || !Boolean.TRUE.equals(mapping.getEnabled())
                        || (mapping.getPermission() != DocumentPermission.READ
                        && mapping.getPermission() != DocumentPermission.WRITE)) {
                    continue;
                }
                visibleUserIds.add(String.valueOf(requirePositive(mapping.getUserId(), "mapping.userId")));
            }
        }
        return new FileIndexDocument(document.getTitle(), FileIndexResourceType.DOCUMENT, String.valueOf(documentId),
                null, new ArrayList<>(visibleUserIds), false, Boolean.TRUE.equals(document.getDeleted()));
    }

    private static Map<Long, List<DocumentUserMappingDO>> groupMappings(List<DocumentUserMappingDO> mappings) {
        Map<Long, List<DocumentUserMappingDO>> result = new HashMap<>();
        if (mappings == null) {
            return result;
        }
        for (DocumentUserMappingDO mapping : mappings) {
            if (mapping == null || mapping.getDocumentId() == null) {
                continue;
            }
            result.computeIfAbsent(mapping.getDocumentId(), ignored -> new ArrayList<>()).add(mapping);
        }
        return result;
    }

    private static long lastNoteId(List<NoteFileIndexSource> page) {
        return requirePositive(page.get(page.size() - 1).id(), "note.id");
    }

    private static long lastImageId(List<MediaFileIndexSource> page) {
        return requirePositive(page.get(page.size() - 1).id(), "image.id");
    }

    private static long requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static <T> List<T> nonNull(List<T> values) {
        return values == null ? List.of() : values;
    }
}
