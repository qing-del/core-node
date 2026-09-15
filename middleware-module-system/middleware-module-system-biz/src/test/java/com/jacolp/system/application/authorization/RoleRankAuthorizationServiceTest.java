package com.jacolp.system.application.authorization;

import com.jacolp.common.core.exception.PermissionDeniedException;
import com.jacolp.system.application.authorization.model.RoleMetadata;
import com.jacolp.system.application.port.out.RoleMetadataRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoleRankAuthorizationServiceTest {

    @Test
    void evaluatesManagementIdentityAndOrderingFromMetadataRatherThanRoleIds() {
        RoleMetadataRepository roles = mock(RoleMetadataRepository.class);
        when(roles.findAll()).thenReturn(List.of(role(91L, "CREATOR", 1), role(8L, "ADMIN", 2), role(3L, "USER", 3)));
        RoleRankAuthorizationService service = new RoleRankAuthorizationService(roles);

        assertThatCode(() -> service.requireManagementRole(91L)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireManagementRole(8L)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireStrictlySuperior(91L, 8L)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireStrictlySuperior(8L, 3L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireManagementRole(3L)).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> service.requireStrictlySuperior(3L, 8L)).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> service.requireStrictlySuperior(8L, 8L)).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void limitsCreatorOnlyOperationByRoleCode() {
        RoleMetadataRepository roles = mock(RoleMetadataRepository.class);
        when(roles.findAll()).thenReturn(List.of(role(10L, "CREATOR", 7), role(20L, "ADMIN", 8)));
        RoleRankAuthorizationService service = new RoleRankAuthorizationService(roles);

        assertThatCode(() -> service.requireCreator(10L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireCreator(20L)).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void malformedOrMissingRoleMetadataFailsClosed() {
        RoleMetadataRepository roles = mock(RoleMetadataRepository.class);
        when(roles.findAll()).thenReturn(List.of(role(2L, "USER", 0)));
        RoleRankAuthorizationService service = new RoleRankAuthorizationService(roles);

        assertThatThrownBy(() -> service.requireManagementRole(2L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.requireManagementRole(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void duplicateRankAndMissingRequestedRoleFailClosed() {
        RoleMetadataRepository roles = mock(RoleMetadataRepository.class);
        when(roles.findAll()).thenReturn(List.of(role(1L, "CREATOR", 1), role(2L, "ADMIN", 1)));
        RoleRankAuthorizationService service = new RoleRankAuthorizationService(roles);

        assertThatThrownBy(() -> service.requireManagementRole(1L)).isInstanceOf(IllegalStateException.class);

        when(roles.findAll()).thenReturn(List.of(role(1L, "CREATOR", 1)));
        assertThatThrownBy(() -> service.requireManagementRole(99L)).isInstanceOf(IllegalStateException.class);
    }

    private static RoleMetadata role(Long id, String code, Integer rank) {
        return new RoleMetadata(id, code, code, rank, 1000, 1_073_741_824L, null, null);
    }
}
