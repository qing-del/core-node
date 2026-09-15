package com.jacolp.system.infrastructure.persistence.repository;

import com.jacolp.system.application.authorization.model.RolePermissionMetadata;
import com.jacolp.system.application.port.out.RolePermissionMetadataRepository;
import com.jacolp.system.infrastructure.persistence.dataobject.RolePermissionDO;
import com.jacolp.system.infrastructure.persistence.mapper.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * MyBatis-backed implementation of direct role-permission metadata persistence.
 */
@Repository
@RequiredArgsConstructor
public class MyBatisRolePermissionMetadataRepository implements RolePermissionMetadataRepository {

    private final RolePermissionMapper rolePermissionMapper;

    @Override
    public List<RolePermissionMetadata> findByRoleIds(Collection<Long> roleIds) {
        List<Long> normalizedRoleIds = normalizeRoleIds(roleIds);
        return normalizedRoleIds.isEmpty() ? List.of() : rolePermissionMapper.selectByRoleIds(normalizedRoleIds).stream()
                .map(MyBatisRolePermissionMetadataRepository::toMetadata)
                .toList();
    }

    @Override
    public int insert(RolePermissionMetadata rolePermission) {
        return rolePermissionMapper.insert(toDataObject(rolePermission));
    }

    @Override
    public int deleteByRoleIdAndPermId(Long roleId, Long permId) {
        return rolePermissionMapper.deleteByRoleIdAndPermId(roleId, permId);
    }

    private static List<Long> normalizeRoleIds(Collection<Long> roleIds) {
        return roleIds == null ? List.of() : roleIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private static RolePermissionMetadata toMetadata(RolePermissionDO rolePermission) {
        return new RolePermissionMetadata(rolePermission.getRoleId(), rolePermission.getPermId(),
                rolePermission.getGrantTime());
    }

    private static RolePermissionDO toDataObject(RolePermissionMetadata rolePermission) {
        RolePermissionDO dataObject = new RolePermissionDO();
        dataObject.setRoleId(rolePermission.roleId());
        dataObject.setPermId(rolePermission.permId());
        dataObject.setGrantTime(rolePermission.grantTime());
        return dataObject;
    }
}
