package com.jacolp.document.application.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.document.api.model.DocumentMetadata;
import com.jacolp.document.application.access.DocumentAccess;
import com.jacolp.document.application.access.DocumentAccessService;
import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DocumentMetadataServiceTest {

    @Test
    void createsDocumentInAuthenticatedPersonalScopeAndNormalizesTitle() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        when(mapper.insert(any(DocumentDO.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, DocumentDO.class).setId(7L);
            return 1;
        });
        DocumentMetadataService service = new DocumentMetadataService(mapper, mock(DocumentRedisRepository.class));

        DocumentMetadata metadata = service.create(42L, "  Project plan  ");

        assertThat(metadata.documentId()).isEqualTo(7L);
        assertThat(metadata.ownerUserId()).isEqualTo(42L);
        assertThat(metadata.title()).isEqualTo("Project plan");
        ArgumentCaptor<DocumentDO> document = ArgumentCaptor.forClass(DocumentDO.class);
        verify(mapper).insert(document.capture());
        assertThat(document.getValue().getOwnerUserId()).isEqualTo(42L);
        assertThat(document.getValue().getLastModifyUserId()).isEqualTo(42L);
        assertThat(document.getValue().getContentObjectKey()).isNull();
        assertThat(document.getValue().getPersistedLogId()).isZero();
    }

    @Test
    void listsDocumentsOwnedOrSharedWithAuthenticatedUser() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        when(mapper.listActiveVisibleByUserId(42L)).thenReturn(List.of(
                document(7L, 42L, "Owned plan"),
                document(8L, 99L, "Shared plan")));
        DocumentMetadataService service = new DocumentMetadataService(mapper, mock(DocumentRedisRepository.class));

        assertThat(service.list(42L)).extracting(DocumentMetadata::documentId).containsExactly(7L, 8L);
        verify(mapper).listActiveVisibleByUserId(42L);
        verify(mapper, never()).listActiveByOwnerUserId(any());
    }

    @Test
    void updatesTitleWithinScopeAndReturnsFreshMetadata() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        when(mapper.updateTitleIfActive(eq(7L), eq(42L), eq("New title"), any(LocalDateTime.class), eq(42L)))
                .thenReturn(1);
        when(mapper.selectActiveById(7L)).thenReturn(document(7L, 42L, "New title"));
        DocumentMetadataService service = new DocumentMetadataService(mapper, mock(DocumentRedisRepository.class));

        assertThat(service.updateTitle(42L, 7L, " New title ").title()).isEqualTo("New title");
    }

    @Test
    void readsSharedDocumentWithEffectivePermissionAndOwnerFlag() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentDO document = document(8L, 99L, "Shared plan");
        when(accessService.requireRead(42L, 8L)).thenReturn(new DocumentAccess(document, DocumentPermission.READ, false));
        DocumentMetadataService service = new DocumentMetadataService(mapper, mock(DocumentRedisRepository.class), accessService);

        DocumentMetadata metadata = service.get(8L, 42L);

        assertThat(metadata.documentId()).isEqualTo(8L);
        assertThat(metadata.ownerUserId()).isEqualTo(99L);
        assertThat(metadata.permission()).isEqualTo("READ");
        assertThat(metadata.owner()).isFalse();
        verify(accessService).requireRead(42L, 8L);
    }

    @Test
    void rejectsDeleteWhileAnyInstanceHasAnActivePresence() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        DocumentRedisRepository redis = mock(DocumentRedisRepository.class);
        when(mapper.selectActiveById(7L)).thenReturn(document(7L, 42L, "Plan"));
        when(redis.countPresence(7L)).thenReturn(1L);
        DocumentMetadataService service = new DocumentMetadataService(mapper, redis);

        assertThatThrownBy(() -> service.delete(42L, 7L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("活跃协作会话");
        verify(mapper, never()).softDeleteByIdAndOwnerUserId(any(), any(), any(), any());
    }

    @Test
    void softDeletesInactiveDocumentWithoutCleaningItsCrdtData() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        DocumentRedisRepository redis = mock(DocumentRedisRepository.class);
        when(mapper.selectActiveById(7L)).thenReturn(document(7L, 42L, "Plan"));
        when(redis.countPresence(7L)).thenReturn(0L);
        when(mapper.softDeleteByIdAndOwnerUserId(eq(7L), eq(42L), any(LocalDateTime.class), eq(42L))).thenReturn(1);
        DocumentMetadataService service = new DocumentMetadataService(mapper, redis);

        service.delete(42L, 7L);

        verify(mapper).softDeleteByIdAndOwnerUserId(eq(7L), eq(42L), any(LocalDateTime.class), eq(42L));
        verify(redis, never()).deleteRoomRuntime(any(Long.class));
    }

    private static DocumentDO document(long id, long ownerUserId, String title) {
        LocalDateTime now = LocalDateTime.now();
        return new DocumentDO(id, ownerUserId, title, null, 0L, now, ownerUserId, false, 0L, now, now);
    }
}
