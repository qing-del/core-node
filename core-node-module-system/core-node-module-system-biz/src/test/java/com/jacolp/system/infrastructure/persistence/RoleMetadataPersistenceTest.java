package com.jacolp.system.infrastructure.persistence;

import com.jacolp.system.application.authorization.model.RoleMetadata;
import com.jacolp.system.application.port.out.RoleMetadataRepository;
import com.jacolp.system.infrastructure.persistence.dataobject.RoleDO;
import com.jacolp.system.infrastructure.persistence.mapper.RoleMapper;
import com.jacolp.system.infrastructure.persistence.repository.MyBatisRoleMetadataRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RoleMetadataPersistenceTest {

    @Test
    void applicationPortExposesOnlyApplicationMetadata() {
        for (Method method : RoleMetadataRepository.class.getDeclaredMethods()) {
            assertThat(method.getGenericReturnType().getTypeName()).doesNotContain(".infrastructure.persistence.");
            for (java.lang.reflect.Type parameterType : method.getGenericParameterTypes()) {
                assertThat(parameterType.getTypeName()).doesNotContain(".infrastructure.persistence.");
            }
        }
    }

    @Test
    void findByIdMapsEveryRoleFieldAndIsNullSafe() {
        RoleMapper mapper = mock(RoleMapper.class);
        MyBatisRoleMetadataRepository repository = new MyBatisRoleMetadataRepository(mapper);
        RoleDO role = role(2L, "管理员", "ADMIN", 2);
        when(mapper.getById(2L)).thenReturn(role);

        Assertions.assertThat(repository.findById(2L)).contains(metadata(role));
        Assertions.assertThat(repository.findById(null)).isEmpty();

        verify(mapper).getById(2L);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void findAllMapsEveryRoleFieldAndSortsByRankThenId() {
        RoleMapper mapper = mock(RoleMapper.class);
        MyBatisRoleMetadataRepository repository = new MyBatisRoleMetadataRepository(mapper);
        RoleDO creator = role(1L, "创建者", "CREATOR", 1);
        RoleDO admin = role(2L, "管理员", "ADMIN", 2);
        RoleDO user = role(3L, "普通用户", "USER", 3);
        when(mapper.getAll()).thenReturn(Arrays.asList(user, null, creator, admin));

        Assertions.assertThat(repository.findAll()).containsExactly(metadata(creator), metadata(admin), metadata(user));

        verify(mapper).getAll();
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void findAllTreatsANullMapperResultAsAnEmptyCatalogue() {
        RoleMapper mapper = mock(RoleMapper.class);
        MyBatisRoleMetadataRepository repository = new MyBatisRoleMetadataRepository(mapper);
        when(mapper.getAll()).thenReturn(null);

        Assertions.assertThat(repository.findAll()).isEmpty();

        verify(mapper).getAll();
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void nullIdDoesNotCallTheMapper() {
        RoleMapper mapper = mock(RoleMapper.class);
        MyBatisRoleMetadataRepository repository = new MyBatisRoleMetadataRepository(mapper);

        Assertions.assertThat(repository.findById(null)).isEmpty();

        verifyNoInteractions(mapper);
    }

    private static RoleDO role(Long id, String roleName, String roleCode, Integer rank) {
        RoleDO role = new RoleDO();
        role.setId(id);
        role.setRoleName(roleName);
        role.setRoleCode(roleCode);
        role.setRank(rank);
        role.setDailyApiLimit(1000);
        role.setMaxStorageBytes(1_073_741_824L);
        role.setCreateTime(LocalDateTime.of(2026, 8, 10, 1, 2, 3));
        role.setUpdateTime(LocalDateTime.of(2026, 8, 10, 1, 2, 4));
        return role;
    }

    private static RoleMetadata metadata(RoleDO role) {
        return new RoleMetadata(role.getId(), role.getRoleName(), role.getRoleCode(), role.getRank(),
                role.getDailyApiLimit(), role.getMaxStorageBytes(), role.getCreateTime(), role.getUpdateTime());
    }
}
