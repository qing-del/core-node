package com.jacolp.system.application.port.out;

import com.jacolp.system.application.authorization.model.RolePermissionMetadata;

import java.util.Collection;
import java.util.List;

/**
 * Persistence port for direct entries in the role-to-permission relation.
 */
public interface RolePermissionMetadataRepository {

    List<RolePermissionMetadata> findByRoleIds(Collection<Long> roleIds);

    default List<RolePermissionMetadata> findByRoleId(Long roleId) {
        return roleId == null ? List.of() : findByRoleIds(List.of(roleId));
    }

    int insert(RolePermissionMetadata rolePermission);

    int deleteByRoleIdAndPermId(Long roleId, Long permId);
}
