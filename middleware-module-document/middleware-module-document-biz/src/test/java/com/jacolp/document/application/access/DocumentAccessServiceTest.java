package com.jacolp.document.application.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentUserMappingDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentUserMappingMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DocumentAccessServiceTest {

    @Test
    void ownerReceivesFullAccessWithoutAnAuthorizationRow() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentUserMappingMapper mappingMapper = mock(DocumentUserMappingMapper.class);
        DocumentDO document = document(7L, 42L);
        when(documentMapper.selectActiveById(7L)).thenReturn(document);
        DocumentAccessService service = new DocumentAccessService(documentMapper, mappingMapper);

        DocumentAccess access = service.requireOwner(7L, 42L);

        assertThat(access.owner()).isTrue();
        assertThat(access.permission()).isEqualTo(DocumentPermission.WRITE);
        assertThat(access.canRead()).isTrue();
        assertThat(access.canWrite()).isTrue();
        verify(mappingMapper, never()).selectEnabledByDocumentIdAndUserId(7L, 42L);
    }

    @Test
    void readMappingCanReadButCannotWrite() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentUserMappingMapper mappingMapper = mock(DocumentUserMappingMapper.class);
        when(documentMapper.selectActiveById(7L)).thenReturn(document(7L, 42L));
        when(mappingMapper.selectEnabledByDocumentIdAndUserId(7L, 43L))
                .thenReturn(mapping(7L, 43L, DocumentPermission.READ));
        DocumentAccessService service = new DocumentAccessService(documentMapper, mappingMapper);

        DocumentAccess access = service.requireRead(7L, 43L);

        assertThat(access.owner()).isFalse();
        assertThat(access.permission()).isEqualTo(DocumentPermission.READ);
        assertThat(access.canRead()).isTrue();
        assertThat(access.canWrite()).isFalse();
        assertThatThrownBy(() -> service.requireWrite(7L, 43L))
                .isInstanceOf(DocumentAccessDeniedException.class)
                .extracting(exception -> ((DocumentAccessDeniedException) exception).reason())
                .isEqualTo(DocumentAccessDeniedException.Reason.FORBIDDEN);
    }

    @Test
    void writeMappingCanReadAndWrite() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentUserMappingMapper mappingMapper = mock(DocumentUserMappingMapper.class);
        when(documentMapper.selectActiveById(7L)).thenReturn(document(7L, 42L));
        when(mappingMapper.selectEnabledByDocumentIdAndUserId(7L, 43L))
                .thenReturn(mapping(7L, 43L, DocumentPermission.WRITE));
        DocumentAccessService service = new DocumentAccessService(documentMapper, mappingMapper);

        DocumentAccess access = service.requireWrite(7L, 43L);

        assertThat(access.owner()).isFalse();
        assertThat(access.permission()).isEqualTo(DocumentPermission.WRITE);
        assertThat(access.canRead()).isTrue();
        assertThat(access.canWrite()).isTrue();
    }

    @Test
    void missingOrRevokedMappingIsForbiddenAndDeletedDocumentIsNotFound() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentUserMappingMapper mappingMapper = mock(DocumentUserMappingMapper.class);
        when(documentMapper.selectActiveById(7L)).thenReturn(document(7L, 42L));
        when(mappingMapper.selectEnabledByDocumentIdAndUserId(7L, 43L)).thenReturn(null);
        DocumentAccessService service = new DocumentAccessService(documentMapper, mappingMapper);

        assertThatThrownBy(() -> service.requireRead(7L, 43L))
                .isInstanceOf(DocumentAccessDeniedException.class)
                .extracting(exception -> ((DocumentAccessDeniedException) exception).reason())
                .isEqualTo(DocumentAccessDeniedException.Reason.FORBIDDEN);

        when(documentMapper.selectActiveById(8L)).thenReturn(null);
        assertThatThrownBy(() -> service.requireRead(8L, 43L))
                .isInstanceOf(DocumentAccessDeniedException.class)
                .extracting(exception -> ((DocumentAccessDeniedException) exception).reason())
                .isEqualTo(DocumentAccessDeniedException.Reason.NOT_FOUND);
    }

    @Test
    void disabledMappingReturnedByAnUnexpectedMapperImplementationIsStillForbidden() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentUserMappingMapper mappingMapper = mock(DocumentUserMappingMapper.class);
        when(documentMapper.selectActiveById(7L)).thenReturn(document(7L, 42L));
        DocumentUserMappingDO disabled = mapping(7L, 43L, DocumentPermission.WRITE);
        disabled.setEnabled(false);
        when(mappingMapper.selectEnabledByDocumentIdAndUserId(7L, 43L)).thenReturn(disabled);
        DocumentAccessService service = new DocumentAccessService(documentMapper, mappingMapper);

        assertThatThrownBy(() -> service.requireRead(7L, 43L))
                .isInstanceOf(DocumentAccessDeniedException.class)
                .extracting(exception -> ((DocumentAccessDeniedException) exception).reason())
                .isEqualTo(DocumentAccessDeniedException.Reason.FORBIDDEN);
    }

    @Test
    void nonOwnerCannotRequireOwnerAccess() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentUserMappingMapper mappingMapper = mock(DocumentUserMappingMapper.class);
        when(documentMapper.selectActiveById(7L)).thenReturn(document(7L, 42L));
        when(mappingMapper.selectEnabledByDocumentIdAndUserId(7L, 43L))
                .thenReturn(mapping(7L, 43L, DocumentPermission.WRITE));
        DocumentAccessService service = new DocumentAccessService(documentMapper, mappingMapper);

        assertThatThrownBy(() -> service.requireOwner(7L, 43L))
                .isInstanceOf(DocumentAccessDeniedException.class);
    }

    private static DocumentDO document(long id, long ownerUserId) {
        LocalDateTime now = LocalDateTime.now();
        return new DocumentDO(id, ownerUserId, "title", null, 0L, now, ownerUserId, false, 0L, now, now);
    }

    private static DocumentUserMappingDO mapping(long documentId, long userId, DocumentPermission permission) {
        LocalDateTime now = LocalDateTime.now();
        return new DocumentUserMappingDO(documentId, userId, permission, true, now, now);
    }
}
