package com.jacolp.system.application.port.out;

import com.jacolp.system.application.authorization.model.PermissionMetadata;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for the permission catalogue and direct, active role grants.
 */
public interface PermissionMetadataRepository {

    Optional<PermissionMetadata> findByCode(String code);

    List<PermissionMetadata> findActiveByRoleIds(Collection<Long> roleIds);

    default List<PermissionMetadata> findActiveByRoleId(Long roleId) {
        return roleId == null ? List.of() : findActiveByRoleIds(List.of(roleId));
    }

    int insert(PermissionMetadata permission);

    int updateById(PermissionMetadata permission);

    int deleteById(Long id);
}
