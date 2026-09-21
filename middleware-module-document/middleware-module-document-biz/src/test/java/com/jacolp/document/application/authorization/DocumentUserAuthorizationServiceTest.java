package com.jacolp.document.application.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.document.application.access.DocumentAccess;
import com.jacolp.document.application.access.DocumentAccessDeniedException;
import com.jacolp.document.application.access.DocumentAccessService;
import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentUserMappingDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentUserMappingMapper;
import com.jacolp.system.api.UserProfileApi;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DocumentUserAuthorizationServiceTest {

    @Test
    void ownerCanListActiveAndRevokedAuthorizations() {
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentUserMappingMapper mappingMapper = mock(DocumentUserMappingMapper.class);
        UserProfileApi userProfileApi = mock(UserProfileApi.class);
        when(accessService.requireOwner(7L, 42L)).thenReturn(ownerAccess());
        DocumentUserMappingDO active = mapping(7L, 43L, DocumentPermission.READ, true);
        DocumentUserMappingDO revoked = mapping(7L, 44L, DocumentPermission.WRITE, false);
        when(mappingMapper.selectByDocumentId(7L)).thenReturn(List.of(active, revoked));

        List<?> authorizations = new DocumentUserAuthorizationService(accessService, mappingMapper, userProfileApi)
                .list(42L, 7L);

        assertThat(authorizations).hasSize(2);
        assertThat(authorizations.get(0)).extracting("userId", "permission", "enabled")
                .containsExactly(43L, DocumentPermission.READ, true);
        assertThat(authorizations.get(1)).extracting("userId", "permission", "enabled")
                .containsExactly(44L, DocumentPermission.WRITE, false);
        verify(accessService).requireOwner(7L, 42L);
    }

    @Test
    void ownerCanUpsertPermissionAndEnabledStateForAnActiveUser() {
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentUserMappingMapper mappingMapper = mock(DocumentUserMappingMapper.class);
        UserProfileApi userProfileApi = mock(UserProfileApi.class);
        when(accessService.requireOwner(7L, 42L)).thenReturn(ownerAccess());
        when(userProfileApi.isActiveUser(43L)).thenReturn(true);
        when(mappingMapper.selectByDocumentIdAndUserId(7L, 43L))
                .thenReturn(mapping(7L, 43L, DocumentPermission.WRITE, false));

        var response = new DocumentUserAuthorizationService(accessService, mappingMapper, userProfileApi)
                .upsert(42L, 7L, 43L, DocumentPermission.WRITE, false);

        ArgumentCaptor<DocumentUserMappingDO> captor = ArgumentCaptor.forClass(DocumentUserMappingDO.class);
        verify(mappingMapper).upsertByDocumentOwner(captor.capture(), eq(42L));
        assertThat(captor.getValue().getDocumentId()).isEqualTo(7L);
        assertThat(captor.getValue().getUserId()).isEqualTo(43L);
        assertThat(captor.getValue().getPermission()).isEqualTo(DocumentPermission.WRITE);
        assertThat(captor.getValue().getEnabled()).isFalse();
        assertThat(response.permission()).isEqualTo(DocumentPermission.WRITE);
        assertThat(response.enabled()).isFalse();
    }

    @Test
    void authorizationRejectsSelfInactiveAndNonOwnerTargets() {
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentUserMappingMapper mappingMapper = mock(DocumentUserMappingMapper.class);
        UserProfileApi userProfileApi = mock(UserProfileApi.class);
        when(accessService.requireOwner(7L, 42L)).thenReturn(ownerAccess());

        DocumentUserAuthorizationService service =
                new DocumentUserAuthorizationService(accessService, mappingMapper, userProfileApi);
        assertThatThrownBy(() -> service.upsert(42L, 7L, 42L, DocumentPermission.READ, true))
                .isInstanceOf(BaseException.class)
                .hasMessage("不能给文档所有者添加授权");

        when(userProfileApi.isActiveUser(43L)).thenReturn(false);
        assertThatThrownBy(() -> service.upsert(42L, 7L, 43L, DocumentPermission.READ, true))
                .isInstanceOf(BaseException.class)
                .hasMessage("被授权用户不存在或未启用");

        when(accessService.requireOwner(7L, 99L)).thenThrow(DocumentAccessDeniedException.forbidden());
        assertThatThrownBy(() -> service.upsert(99L, 7L, 43L, DocumentPermission.READ, true))
                .isInstanceOf(DocumentAccessDeniedException.class);
        verify(userProfileApi, never()).isActiveUser(99L);
        verify(mappingMapper, never()).upsertByDocumentOwner(any(), any());
    }

    @Test
    void revokeOnlyDisablesAuthorizationAndIsIdempotent() {
        DocumentAccessService accessService = mock(DocumentAccessService.class);
        DocumentUserMappingMapper mappingMapper = mock(DocumentUserMappingMapper.class);
        UserProfileApi userProfileApi = mock(UserProfileApi.class);
        when(accessService.requireOwner(7L, 42L)).thenReturn(ownerAccess());

        DocumentUserAuthorizationService service =
                new DocumentUserAuthorizationService(accessService, mappingMapper, userProfileApi);
        service.revoke(42L, 7L, 43L);
        service.revoke(42L, 7L, 43L);
        service.revoke(42L, 7L, 42L);

        verify(mappingMapper, org.mockito.Mockito.times(2)).disableByDocumentOwner(7L, 43L, 42L);
        verify(mappingMapper).disableByDocumentOwner(7L, 42L, 42L);
    }

    private static DocumentAccess ownerAccess() {
        return new DocumentAccess(document(7L, 42L), DocumentPermission.WRITE, true);
    }

    private static DocumentDO document(long id, long ownerUserId) {
        LocalDateTime now = LocalDateTime.now();
        return new DocumentDO(id, ownerUserId, "title", null, 0L, now, ownerUserId, false, 0L, now, now);
    }

    private static DocumentUserMappingDO mapping(long documentId, long userId,
                                                  DocumentPermission permission, boolean enabled) {
        LocalDateTime now = LocalDateTime.now();
        return new DocumentUserMappingDO(documentId, userId, permission, enabled, now, now);
    }
}
