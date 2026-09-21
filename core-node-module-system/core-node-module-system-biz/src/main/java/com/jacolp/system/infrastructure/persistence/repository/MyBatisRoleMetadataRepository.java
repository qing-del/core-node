package com.jacolp.system.infrastructure.persistence.repository;

import com.jacolp.system.application.authorization.model.RoleMetadata;
import com.jacolp.system.application.port.out.RoleMetadataRepository;
import com.jacolp.system.infrastructure.persistence.dataobject.RoleDO;
import com.jacolp.system.infrastructure.persistence.mapper.RoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * MyBatis-backed read adapter for application role metadata.
 */
@Repository
@RequiredArgsConstructor
public class MyBatisRoleMetadataRepository implements RoleMetadataRepository {

    private static final Comparator<RoleMetadata> STABLE_ROLE_ORDER = Comparator
            .comparing(RoleMetadata::rank, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(RoleMetadata::id, Comparator.nullsLast(Comparator.naturalOrder()));

    private final RoleMapper roleMapper;

    @Override
    public Optional<RoleMetadata> findById(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(roleMapper.getById(id))
                .map(MyBatisRoleMetadataRepository::toMetadata);
    }

    @Override
    public List<RoleMetadata> findAll() {
        List<RoleDO> roles = roleMapper.getAll();
        return roles == null ? List.of() : roles.stream()
                .filter(Objects::nonNull)
                .map(MyBatisRoleMetadataRepository::toMetadata)
                .sorted(STABLE_ROLE_ORDER)
                .toList();
    }

    private static RoleMetadata toMetadata(RoleDO role) {
        return new RoleMetadata(role.getId(), role.getRoleName(), role.getRoleCode(), role.getRank(),
                role.getDailyApiLimit(), role.getMaxStorageBytes(), role.getCreateTime(), role.getUpdateTime());
    }
}
