package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.PermissionMetadata;
import com.jacolp.system.application.authorization.model.RoleMetadata;
import com.jacolp.system.application.port.out.PermissionMetadataRepository;
import com.jacolp.system.application.port.out.RoleMetadataRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class EffectiveRolePermissionResolverTest {

    private static final RoleMetadata CREATOR = role(1L, "CREATOR", 1);
    private static final RoleMetadata ADMIN = role(2L, "ADMIN", 2);
    private static final RoleMetadata USER = role(3L, "USER", 3);
    private static final List<RoleMetadata> ROLE_CATALOGUE = List.of(CREATOR, ADMIN, USER);

    @Test
    void userReceivesOnlyItsOwnRankPermissionsInOneBatch() {
        Fixture fixture = fixture(USER, ROLE_CATALOGUE);
        when(fixture.permissions.findActiveByRoleIds(List.of(3L)))
                .thenReturn(List.of(permission(1L, "*:write", "*", "write"),
                        permission(2L, "*:read", "*", "read")));

        EffectiveRolePermissions actual = fixture.resolver.resolve(3L);

        assertThat(actual).isEqualTo(new EffectiveRolePermissions(3L, "USER", 3,
                List.of("*:read", "*:write")));
        verifySingleBatch(fixture, 3L, List.of(3L));
    }

    @Test
    void adminInheritsAdminAndUserPermissions() {
        Fixture fixture = fixture(ADMIN, ROLE_CATALOGUE);
        when(fixture.permissions.findActiveByRoleIds(List.of(2L, 3L)))
                .thenReturn(List.of(permission(1L, "*:manage", "*", "manage"),
                        permission(2L, "*:read", "*", "read")));

        Assertions.assertThat(fixture.resolver.resolve(2L).permissionCodes())
                .containsExactly("*:manage", "*:read");
        verifySingleBatch(fixture, 2L, List.of(2L, 3L));
    }

    @Test
    void creatorInheritsEveryRankAndPreservesWildcardCodes() {
        Fixture fixture = fixture(CREATOR, ROLE_CATALOGUE);
        when(fixture.permissions.findActiveByRoleIds(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(permission(1L, "*:super", "*", "super"),
                        permission(2L, "*:manage", "*", "manage"),
                        permission(3L, "*:read", "*", "read")));

        Assertions.assertThat(fixture.resolver.resolve(1L).permissionCodes())
                .containsExactly("*:manage", "*:read", "*:super");
        verifySingleBatch(fixture, 1L, List.of(1L, 2L, 3L));
    }

    @Test
    void duplicateEquivalentPermissionCodesAreDeduplicatedInStableOrder() {
        Fixture fixture = fixture(USER, ROLE_CATALOGUE);
        PermissionMetadata read = permission(1L, "*:read", "*", "read");
        when(fixture.permissions.findActiveByRoleIds(List.of(3L))).thenReturn(List.of(read, read,
                permission(2L, "*:write", "*", "write")));

        Assertions.assertThat(fixture.resolver.resolve(3L).permissionCodes()).containsExactly("*:read", "*:write");
        verifySingleBatch(fixture, 3L, List.of(3L));
    }

    @Test
    void inactiveOrMalformedPermissionsFailClosed() {
        Fixture inactive = fixture(USER, ROLE_CATALOGUE);
        when(inactive.permissions.findActiveByRoleIds(List.of(3L)))
                .thenReturn(List.of(permissionWithStatus(1L, "*:read", "*", "read", "inactive")));

        assertThatIllegalStateException().isThrownBy(() -> inactive.resolver.resolve(3L));
        verifySingleBatch(inactive, 3L, List.of(3L));

        Fixture malformed = fixture(USER, ROLE_CATALOGUE);
        when(malformed.permissions.findActiveByRoleIds(List.of(3L)))
                .thenReturn(List.of(permission(1L, "*:read", "", "read")));

        assertThatIllegalStateException().isThrownBy(() -> malformed.resolver.resolve(3L));
        verifySingleBatch(malformed, 3L, List.of(3L));
    }

    @Test
    void duplicateOrInconsistentRoleCatalogueFailsClosedWithoutPermissionQuery() {
        Fixture duplicateRank = fixture(ADMIN, Arrays.asList(CREATOR, ADMIN, role(4L, "OTHER", 2)));

        assertThatIllegalStateException().isThrownBy(() -> duplicateRank.resolver.resolve(2L));
        verify(duplicateRank.roles).findById(2L);
        verify(duplicateRank.roles).findAll();
        Mockito.verifyNoMoreInteractions(duplicateRank.roles);
        Mockito.verifyNoInteractions(duplicateRank.permissions);

        Fixture duplicateId = fixture(ADMIN, List.of(CREATOR, ADMIN, role(2L, "OTHER", 4)));

        assertThatIllegalStateException().isThrownBy(() -> duplicateId.resolver.resolve(2L));
        verify(duplicateId.roles).findById(2L);
        verify(duplicateId.roles).findAll();
        Mockito.verifyNoMoreInteractions(duplicateId.roles);
        Mockito.verifyNoInteractions(duplicateId.permissions);

        Fixture inconsistent = fixture(ADMIN, List.of(CREATOR, role(2L, "ADMIN", 4), USER));

        assertThatIllegalStateException().isThrownBy(() -> inconsistent.resolver.resolve(2L));
        verify(inconsistent.roles).findById(2L);
        verify(inconsistent.roles).findAll();
        Mockito.verifyNoMoreInteractions(inconsistent.roles);
        Mockito.verifyNoInteractions(inconsistent.permissions);
    }

    @Test
    void missingOrInvalidCurrentRoleFailsClosedWithoutCatalogueOrPermissionQueries() {
        RoleMetadataRepository roles = mock(RoleMetadataRepository.class);
        PermissionMetadataRepository permissions = mock(PermissionMetadataRepository.class);
        EffectiveRolePermissionResolver resolver = new EffectiveRolePermissionResolver(roles, permissions);
        when(roles.findById(3L)).thenReturn(Optional.empty());

        assertThatIllegalStateException().isThrownBy(() -> resolver.resolve(3L));
        verify(roles).findById(3L);
        verifyNoMoreInteractions(roles);
        verifyNoInteractions(permissions);

        RoleMetadataRepository invalidRoles = mock(RoleMetadataRepository.class);
        PermissionMetadataRepository invalidPermissions = mock(PermissionMetadataRepository.class);
        EffectiveRolePermissionResolver invalidResolver = new EffectiveRolePermissionResolver(invalidRoles, invalidPermissions);
        when(invalidRoles.findById(3L)).thenReturn(Optional.of(role(3L, "", 3)));

        assertThatIllegalStateException().isThrownBy(() -> invalidResolver.resolve(3L));
        verify(invalidRoles).findById(3L);
        verifyNoMoreInteractions(invalidRoles);
        verifyNoInteractions(invalidPermissions);
    }

    @Test
    void conflictingPermissionMetadataForOneCodeFailsClosed() {
        Fixture fixture = fixture(USER, ROLE_CATALOGUE);
        when(fixture.permissions.findActiveByRoleIds(List.of(3L))).thenReturn(List.of(
                permission(1L, "*:read", "*", "read"), permission(2L, "*:read", "note", "read")));

        assertThatIllegalStateException().isThrownBy(() -> fixture.resolver.resolve(3L));
        verifySingleBatch(fixture, 3L, List.of(3L));
    }

    private static Fixture fixture(RoleMetadata current, List<RoleMetadata> catalogue) {
        RoleMetadataRepository roles = mock(RoleMetadataRepository.class);
        PermissionMetadataRepository permissions = mock(PermissionMetadataRepository.class);
        when(roles.findById(current.id())).thenReturn(Optional.of(current));
        when(roles.findAll()).thenReturn(catalogue);
        return new Fixture(roles, permissions, new EffectiveRolePermissionResolver(roles, permissions));
    }

    private static void verifySingleBatch(Fixture fixture, Long currentRoleId, List<Long> roleIds) {
        verify(fixture.roles).findById(currentRoleId);
        verify(fixture.roles).findAll();
        verify(fixture.permissions).findActiveByRoleIds(roleIds);
        verifyNoMoreInteractions(fixture.roles, fixture.permissions);
    }

    private static RoleMetadata role(Long id, String roleCode, Integer rank) {
        return new RoleMetadata(id, roleCode, roleCode, rank, 1000, 1_073_741_824L,
                LocalDateTime.of(2026, 8, 10, 1, 2, 3), LocalDateTime.of(2026, 8, 10, 1, 2, 4));
    }

    private static PermissionMetadata permission(Long id, String code, String resource, String action) {
        return permissionWithStatus(id, code, resource, action, "active");
    }

    private static PermissionMetadata permissionWithStatus(Long id, String code, String resource, String action,
                                                            String status) {
        return new PermissionMetadata(id, code, null, resource, action, status, code,
                LocalDateTime.of(2026, 8, 10, 1, 2, 3), LocalDateTime.of(2026, 8, 10, 1, 2, 4));
    }

    private record Fixture(RoleMetadataRepository roles, PermissionMetadataRepository permissions,
                           EffectiveRolePermissionResolver resolver) {
    }
}
