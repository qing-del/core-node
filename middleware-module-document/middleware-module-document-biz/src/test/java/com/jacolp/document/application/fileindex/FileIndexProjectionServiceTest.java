package com.jacolp.document.application.fileindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentUserMappingDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentUserMappingMapper;
import com.jacolp.framework.elasticsearch.ElasticsearchIndexResolver;
import com.jacolp.framework.elasticsearch.ElasticsearchOperations;
import com.jacolp.media.api.MediaFileIndexApi;
import com.jacolp.media.api.model.MediaFileIndexSource;
import com.jacolp.note.api.NoteFileIndexApi;
import com.jacolp.note.api.model.NoteFileIndexSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FileIndexProjectionServiceTest {

    private ElasticsearchClient elasticsearchClient;
    private ElasticsearchOperations elasticsearchOperations;
    private ElasticsearchIndexResolver indexResolver;
    private NoteFileIndexApi noteFileIndexApi;
    private MediaFileIndexApi mediaFileIndexApi;
    private DocumentMapper documentMapper;
    private DocumentUserMappingMapper documentUserMappingMapper;
    private FileIndexProjectionService service;

    @BeforeEach
    void setUp() {
        elasticsearchClient = mock(ElasticsearchClient.class);
        elasticsearchOperations = mock(ElasticsearchOperations.class);
        indexResolver = mock(ElasticsearchIndexResolver.class);
        noteFileIndexApi = mock(NoteFileIndexApi.class);
        mediaFileIndexApi = mock(MediaFileIndexApi.class);
        documentMapper = mock(DocumentMapper.class);
        documentUserMappingMapper = mock(DocumentUserMappingMapper.class);
        when(indexResolver.requireIndex("file")).thenReturn("file-v07");
        service = new FileIndexProjectionService(elasticsearchClient, elasticsearchOperations,
                indexResolver, noteFileIndexApi, mediaFileIndexApi, documentMapper, documentUserMappingMapper);
    }

    @Test
    void refreshesNoteWithOwnerVisibilityAndStableId() {
        when(noteFileIndexApi.findById(7L)).thenReturn(Optional.of(
                new NoteFileIndexSource(7L, 42L, "设计笔记", true, false)));

        service.refresh(new FileIndexResourceKey(FileIndexResourceType.NOTE, 7L));

        ArgumentCaptor<FileIndexDocument> document = ArgumentCaptor.forClass(FileIndexDocument.class);
        verify(elasticsearchOperations).index(eq("file-v07"), eq("NOTE:7"), document.capture());
        assertThat(document.getValue()).isEqualTo(new FileIndexDocument("设计笔记", FileIndexResourceType.NOTE,
                "7", null, List.of("42"), true, false));
        verify(mediaFileIndexApi, never()).findById(any());
        verify(documentMapper, never()).selectById(any());
    }

    @Test
    void refreshesImageWithUrlAndDeletesProjectionForHardDeletedImage() {
        when(mediaFileIndexApi.findById(8L)).thenReturn(Optional.of(
                new MediaFileIndexSource(8L, 42L, "cover.png", "https://oss.test/cover.png", false, false)));

        service.refresh(new FileIndexResourceKey(FileIndexResourceType.IMAGE, 8L));

        ArgumentCaptor<FileIndexDocument> indexed = ArgumentCaptor.forClass(FileIndexDocument.class);
        verify(elasticsearchOperations).index(eq("file-v07"), eq("IMAGE:8"), indexed.capture());
        assertThat(indexed.getValue().resourceUrl()).isEqualTo("https://oss.test/cover.png");
        assertThat(indexed.getValue().isPublic()).isFalse();

        when(mediaFileIndexApi.findById(8L)).thenReturn(Optional.empty());
        service.refresh(new FileIndexResourceKey(FileIndexResourceType.IMAGE, 8L));
        verify(elasticsearchOperations).delete("file-v07", "IMAGE:8");
    }

    @Test
    void refreshesDocumentWithOwnerAndEnabledReadableMappingsOnly() {
        when(documentMapper.selectById(9L)).thenReturn(document(9L, 42L, "协作文档", false));
        when(documentUserMappingMapper.selectEnabledReadableByDocumentIds(List.of(9L))).thenReturn(List.of(
                mapping(9L, 2L, DocumentPermission.READ, true),
                mapping(9L, 3L, DocumentPermission.WRITE, true),
                mapping(9L, 4L, DocumentPermission.WRITE, false)));

        service.refresh(new FileIndexResourceKey(FileIndexResourceType.DOCUMENT, 9L));

        ArgumentCaptor<FileIndexDocument> document = ArgumentCaptor.forClass(FileIndexDocument.class);
        verify(elasticsearchOperations).index(eq("file-v07"), eq("DOCUMENT:9"), document.capture());
        assertThat(document.getValue().fileName()).isEqualTo("协作文档");
        assertThat(document.getValue().visibleUserIds()).containsExactlyInAnyOrder("42", "2", "3");
        assertThat(document.getValue().isPublic()).isFalse();
        assertThat(document.getValue().isDelete()).isFalse();
        assertThat(document.getValue().resourceUrl()).isNull();
    }

    @Test
    void keepsSoftDeletedDocumentAsAnIndexSource() {
        when(documentMapper.selectById(10L)).thenReturn(document(10L, 42L, "已删除文档", true));
        when(documentUserMappingMapper.selectEnabledReadableByDocumentIds(List.of(10L))).thenReturn(List.of());

        service.refresh(new FileIndexResourceKey(FileIndexResourceType.DOCUMENT, 10L));

        ArgumentCaptor<FileIndexDocument> document = ArgumentCaptor.forClass(FileIndexDocument.class);
        verify(elasticsearchOperations).index(eq("file-v07"), eq("DOCUMENT:10"), document.capture());
        assertThat(document.getValue().isDelete()).isTrue();
    }

    @Test
    void rebuildsAllSourcesAfterClearingTheExistingProjection() {
        when(noteFileIndexApi.listAfterId(0L, FileIndexProjectionService.DEFAULT_PAGE_SIZE)).thenReturn(List.of(
                new NoteFileIndexSource(7L, 42L, "笔记", false, true)));
        when(mediaFileIndexApi.listAfterId(0L, FileIndexProjectionService.DEFAULT_PAGE_SIZE)).thenReturn(List.of(
                new MediaFileIndexSource(8L, 42L, "图片.png", "https://oss.test/image.png", true, false)));
        when(documentMapper.selectByIdAfter(0L, FileIndexProjectionService.DEFAULT_PAGE_SIZE)).thenReturn(List.of(
                document(9L, 42L, "文档", false)));
        when(documentUserMappingMapper.selectEnabledReadableByDocumentIds(List.of(9L))).thenReturn(List.of());

        service.rebuildAll();

        verify(elasticsearchClient).deleteByQuery(any(Function.class));
        ArgumentCaptor<String> ids = ArgumentCaptor.forClass(String.class);
        verify(elasticsearchOperations, times(3)).index(eq("file-v07"), ids.capture(), any(FileIndexDocument.class));
        assertThat(ids.getAllValues()).containsExactlyInAnyOrder("NOTE:7", "IMAGE:8", "DOCUMENT:9");
        verify(noteFileIndexApi).listAfterId(0L, FileIndexProjectionService.DEFAULT_PAGE_SIZE);
        verify(mediaFileIndexApi).listAfterId(0L, FileIndexProjectionService.DEFAULT_PAGE_SIZE);
    }

    private static DocumentDO document(long id, long ownerUserId, String title, boolean deleted) {
        LocalDateTime now = LocalDateTime.now();
        return new DocumentDO(id, ownerUserId, title, null, 0L, now, ownerUserId, deleted, 0L, now, now);
    }

    private static DocumentUserMappingDO mapping(long documentId, long userId, DocumentPermission permission,
                                                  boolean enabled) {
        LocalDateTime now = LocalDateTime.now();
        return new DocumentUserMappingDO(documentId, userId, permission, enabled, now, now);
    }
}
