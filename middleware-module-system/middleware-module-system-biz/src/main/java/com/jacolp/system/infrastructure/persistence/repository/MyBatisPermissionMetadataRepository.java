package com.jacolp.system.infrastructure.persistence.repository;

import com.jacolp.system.application.authorization.model.PermissionMetadata;
import com.jacolp.system.application.port.out.PermissionMetadataRepository;
import com.jacolp.system.infrastructure.persistence.dataobject.PermissionDO;
import com.jacolp.system.infrastructure.persistence.mapper.PermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * MyBatis-backed implementation of permission metadata persistence.
 */
@Repository
@RequiredArgsConstructor
public class MyBatisPermissionMetadataRepository implements PermissionMetadataRepository {

    private final PermissionMapper permissionMapper;

    @Override
    public Optional<PermissionMetadata> findByCode(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(permissionMapper.selectByCode(code))
                .map(MyBatisPermissionMetadataRepository::toMetadata);
    }

    @Override
    public List<PermissionMetadata> findActiveByRoleIds(Collection<Long> roleIds) {
        List<Long> normalizedRoleIds = normalizeRoleIds(roleIds);
        return normalizedRoleIds.isEmpty() ? List.of() : permissionMapper.selectActiveByRoleIds(normalizedRoleIds).stream()
                .map(MyBatisPermissionMetadataRepository::toMetadata)
                .toList();
    }

    @Override
    public int insert(PermissionMetadata permission) {
        return permissionMapper.insert(toDataObject(permission));
    }

    @Override
    public int updateById(PermissionMetadata permission) {
        return permissionMapper.updateById(toDataObject(permission));
    }

    @Override
    public int deleteById(Long id) {
        return permissionMapper.deleteById(id);
    }

    private static List<Long> normalizeRoleIds(Collection<Long> roleIds) {
        return roleIds == null ? List.of() : roleIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private static PermissionMetadata toMetadata(PermissionDO permission) {
        return new PermissionMetadata(permission.getId(), permission.getCode(), permission.getOauthScope(),
                permission.getResource(), permission.getAction(), permission.getStatus(), permission.getDescription(),
                permission.getCreateTime(), permission.getUpdateTime());
    }

    private static PermissionDO toDataObject(PermissionMetadata permission) {
        PermissionDO dataObject = new PermissionDO();
        dataObject.setId(permission.id());
        dataObject.setCode(permission.code());
        dataObject.setOauthScope(permission.oauthScope());
        dataObject.setResource(permission.resource());
        dataObject.setAction(permission.action());
        dataObject.setStatus(permission.status());
        dataObject.setDescription(permission.description());
        dataObject.setCreateTime(permission.createTime());
        dataObject.setUpdateTime(permission.updateTime());
        return dataObject;
    }
}
