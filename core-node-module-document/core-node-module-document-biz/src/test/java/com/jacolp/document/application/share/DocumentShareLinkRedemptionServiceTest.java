package com.jacolp.document.application.share;

import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.common.security.oauth2.token.OpaqueTokenProtector;
import com.jacolp.document.application.access.DocumentAccessDeniedException;
import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentShareLinkDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentShareLinkRedemptionDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentUserMappingDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentShareLinkMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentUserMappingMapper;
import com.jacolp.system.api.UserProfileApi;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentShareLinkRedemptionServiceTest {

    @Test
    void grantsReadOnceAndDoesNotCountAnIdempotentRedemptionTwice() {
        DocumentShareLinkMapper links = mock(DocumentShareLinkMapper.class);
        DocumentMapper documents = mock(DocumentMapper.class);
        DocumentUserMappingMapper mappings = mock(DocumentUserMappingMapper.class);
        UserProfileApi users = mock(UserProfileApi.class);
        OpaqueTokenProtector protector = mock(OpaqueTokenProtector.class);
        DocumentShareLinkRedemptionService service = new DocumentShareLinkRedemptionService(links, documents, mappings, users, protector);
        CurrentPrincipal principal = principal(2L, "document:read");
        DocumentShareLinkDO link = link(DocumentPermission.READ, 0);
        when(protector.fingerprint("code")).thenReturn("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        when(links.selectByTokenHash(any())).thenReturn(link);
        when(links.selectByIdForUpdate(7L)).thenReturn(link);
        when(documents.selectActiveById(42L)).thenReturn(document());
        when(users.isActiveUser(2L)).thenReturn(true);
        when(mappings.selectByDocumentIdAndUserId(42L, 2L)).thenReturn(null).thenReturn(
                new DocumentUserMappingDO(42L, 2L, DocumentPermission.READ, true, null, null));
        when(links.selectRedemption(7L, 2L)).thenReturn(null).thenReturn(
                new DocumentShareLinkRedemptionDO(7L, 2L, DocumentPermission.READ, LocalDateTime.now()));
        when(mappings.upsertByDocumentOwner(any(), eq(1L))).thenReturn(1);
        when(links.insertRedemption(any())).thenReturn(1);
        when(links.incrementUsedCountIfAvailable(7L)).thenReturn(1);

        assertThat(service.redeem(principal, "code").permission()).isEqualTo(DocumentPermission.READ);
        assertThat(service.redeem(principal, "code").permission()).isEqualTo(DocumentPermission.READ);
        verify(links).incrementUsedCountIfAvailable(7L);
    }

    @Test
    void writeLinkRequiresGlobalWriteScope() {
        DocumentShareLinkMapper links = mock(DocumentShareLinkMapper.class);
        OpaqueTokenProtector protector = mock(OpaqueTokenProtector.class);
        when(protector.fingerprint("code")).thenReturn("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        DocumentShareLinkDO link = link(DocumentPermission.WRITE, 0);
        when(links.selectByTokenHash(any())).thenReturn(link);
        when(links.selectByIdForUpdate(7L)).thenReturn(link);
        DocumentShareLinkRedemptionService service = new DocumentShareLinkRedemptionService(links, mock(DocumentMapper.class),
                mock(DocumentUserMappingMapper.class), mock(UserProfileApi.class), protector);
        assertThatThrownBy(() -> service.redeem(principal(2L, "document:read"), "code"))
                .isInstanceOf(com.jacolp.common.core.exception.PermissionDeniedException.class);
    }


    @Test
    void disabledWriteMappingDoesNotUpgradeReadLink() {
        DocumentShareLinkMapper links = mock(DocumentShareLinkMapper.class);
        DocumentMapper documents = mock(DocumentMapper.class);
        DocumentUserMappingMapper mappings = mock(DocumentUserMappingMapper.class);
        UserProfileApi users = mock(UserProfileApi.class);
        OpaqueTokenProtector protector = mock(OpaqueTokenProtector.class);
        DocumentShareLinkRedemptionService service = new DocumentShareLinkRedemptionService(links, documents, mappings, users, protector);
        DocumentShareLinkDO link = link(DocumentPermission.READ, 0);
        DocumentUserMappingDO disabledWrite = new DocumentUserMappingDO(42L, 2L, DocumentPermission.WRITE,
                false, null, null);
        when(protector.fingerprint("code")).thenReturn("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        when(links.selectByTokenHash(any())).thenReturn(link);
        when(links.selectByIdForUpdate(7L)).thenReturn(link);
        when(documents.selectActiveById(42L)).thenReturn(document());
        when(users.isActiveUser(2L)).thenReturn(true);
        when(mappings.selectByDocumentIdAndUserId(42L, 2L)).thenReturn(disabledWrite);
        when(links.selectRedemption(7L, 2L)).thenReturn(null);
        when(mappings.upsertByDocumentOwner(any(), eq(1L))).thenReturn(1);
        when(links.insertRedemption(any())).thenReturn(1);
        when(links.incrementUsedCountIfAvailable(7L)).thenReturn(1);

        assertThat(service.redeem(principal(2L, "document:read"), "code").permission())
                .isEqualTo(DocumentPermission.READ);
        org.mockito.ArgumentCaptor<DocumentUserMappingDO> grant = org.mockito.ArgumentCaptor.forClass(DocumentUserMappingDO.class);
        verify(mappings).upsertByDocumentOwner(grant.capture(), eq(1L));
        assertThat(grant.getValue().getPermission()).isEqualTo(DocumentPermission.READ);
        assertThat(grant.getValue().getEnabled()).isTrue();
    }

    @Test
    void existingRedemptionDoesNotTreatDisabledMappingAsEffective() {
        DocumentShareLinkMapper links = mock(DocumentShareLinkMapper.class);
        DocumentMapper documents = mock(DocumentMapper.class);
        DocumentUserMappingMapper mappings = mock(DocumentUserMappingMapper.class);
        UserProfileApi users = mock(UserProfileApi.class);
        OpaqueTokenProtector protector = mock(OpaqueTokenProtector.class);
        DocumentShareLinkRedemptionService service = new DocumentShareLinkRedemptionService(links, documents, mappings, users, protector);
        DocumentShareLinkDO link = link(DocumentPermission.READ, 1);
        DocumentUserMappingDO disabledWrite = new DocumentUserMappingDO(42L, 2L, DocumentPermission.WRITE,
                false, null, null);
        when(protector.fingerprint("code")).thenReturn("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        when(links.selectByTokenHash(any())).thenReturn(link);
        when(links.selectByIdForUpdate(7L)).thenReturn(link);
        when(documents.selectActiveById(42L)).thenReturn(document());
        when(users.isActiveUser(2L)).thenReturn(true);
        when(mappings.selectByDocumentIdAndUserId(42L, 2L)).thenReturn(disabledWrite);
        when(links.selectRedemption(7L, 2L)).thenReturn(
                new DocumentShareLinkRedemptionDO(7L, 2L, DocumentPermission.READ, LocalDateTime.now()));

        assertThatThrownBy(() -> service.redeem(principal(2L, "document:read"), "code"))
                .isInstanceOf(DocumentAccessDeniedException.class);
        verify(mappings, org.mockito.Mockito.never()).upsertByDocumentOwner(any(), any());
        verify(links, org.mockito.Mockito.never()).incrementUsedCountIfAvailable(7L);
    }
    @Test
    void existingWriteRedemptionUsesCurrentReadMappingPermission() {
        DocumentShareLinkMapper links = mock(DocumentShareLinkMapper.class);
        DocumentMapper documents = mock(DocumentMapper.class);
        DocumentUserMappingMapper mappings = mock(DocumentUserMappingMapper.class);
        UserProfileApi users = mock(UserProfileApi.class);
        OpaqueTokenProtector protector = mock(OpaqueTokenProtector.class);
        DocumentShareLinkRedemptionService service = new DocumentShareLinkRedemptionService(links, documents, mappings, users, protector);
        DocumentShareLinkDO link = link(DocumentPermission.WRITE, 1);
        when(protector.fingerprint("code")).thenReturn("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        when(links.selectByTokenHash(any())).thenReturn(link);
        when(links.selectByIdForUpdate(7L)).thenReturn(link);
        when(documents.selectActiveById(42L)).thenReturn(document());
        when(users.isActiveUser(2L)).thenReturn(true);
        when(mappings.selectByDocumentIdAndUserId(42L, 2L)).thenReturn(
                new DocumentUserMappingDO(42L, 2L, DocumentPermission.READ, true, null, null));
        when(links.selectRedemption(7L, 2L)).thenReturn(
                new DocumentShareLinkRedemptionDO(7L, 2L, DocumentPermission.WRITE, LocalDateTime.now()));

        assertThat(service.redeem(principal(2L, "document:write"), "code").permission())
                .isEqualTo(DocumentPermission.READ);
        verify(mappings, org.mockito.Mockito.never()).upsertByDocumentOwner(any(), any());
        verify(links, org.mockito.Mockito.never()).incrementUsedCountIfAvailable(7L);
    }

    @Test
    void existingReadRedemptionUsesCurrentWriteMappingPermission() {
        DocumentShareLinkMapper links = mock(DocumentShareLinkMapper.class);
        DocumentMapper documents = mock(DocumentMapper.class);
        DocumentUserMappingMapper mappings = mock(DocumentUserMappingMapper.class);
        UserProfileApi users = mock(UserProfileApi.class);
        OpaqueTokenProtector protector = mock(OpaqueTokenProtector.class);
        DocumentShareLinkRedemptionService service = new DocumentShareLinkRedemptionService(links, documents, mappings, users, protector);
        DocumentShareLinkDO link = link(DocumentPermission.READ, 1);
        when(protector.fingerprint("code")).thenReturn("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        when(links.selectByTokenHash(any())).thenReturn(link);
        when(links.selectByIdForUpdate(7L)).thenReturn(link);
        when(documents.selectActiveById(42L)).thenReturn(document());
        when(users.isActiveUser(2L)).thenReturn(true);
        when(mappings.selectByDocumentIdAndUserId(42L, 2L)).thenReturn(
                new DocumentUserMappingDO(42L, 2L, DocumentPermission.WRITE, true, null, null));
        when(links.selectRedemption(7L, 2L)).thenReturn(
                new DocumentShareLinkRedemptionDO(7L, 2L, DocumentPermission.READ, LocalDateTime.now()));

        assertThat(service.redeem(principal(2L, "document:read"), "code").permission())
                .isEqualTo(DocumentPermission.WRITE);
        verify(mappings, org.mockito.Mockito.never()).upsertByDocumentOwner(any(), any());
        verify(links, org.mockito.Mockito.never()).incrementUsedCountIfAvailable(7L);
    }
    private static DocumentShareLinkDO link(DocumentPermission permission, int used) {
        return new DocumentShareLinkDO(7L, 42L, 1L, new byte[32], permission, LocalDateTime.now().plusHours(1),
                3, used, true, null, null, null);
    }

    private static DocumentDO document() {
        return new DocumentDO(42L, 1L, "doc", null, null, null, null, false, 0L, null, null);
    }

    private static CurrentPrincipal principal(long id, String scope) {
        return new CurrentPrincipal(id, "user", "user", "password", List.of("USER"), List.of(scope));
    }
}
