package com.jacolp.middleware.module.system.biz.infrastructure.persistence;

import com.jacolp.system.application.authorization.model.PermissionMetadata;
import com.jacolp.system.application.authorization.model.RolePermissionMetadata;
import com.jacolp.system.application.port.out.PermissionMetadataRepository;
import com.jacolp.system.application.port.out.RolePermissionMetadataRepository;
import com.jacolp.system.infrastructure.persistence.dataobject.PermissionDO;
import com.jacolp.system.infrastructure.persistence.dataobject.RolePermissionDO;
import com.jacolp.system.infrastructure.persistence.mapper.PermissionMapper;
import com.jacolp.system.infrastructure.persistence.mapper.RolePermissionMapper;
import com.jacolp.system.infrastructure.persistence.repository.MyBatisPermissionMetadataRepository;
import com.jacolp.system.infrastructure.persistence.repository.MyBatisRolePermissionMetadataRepository;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RbacMetadataPersistenceTest {

    @Test
    void applicationPortsExposeOnlyApplicationMetadataModels() {
        assertNoInfrastructurePersistenceType(PermissionMetadataRepository.class);
        assertNoInfrastructurePersistenceType(RolePermissionMetadataRepository.class);
    }

    @Test
    void mapperXmlDefinesTheRbacMetadataStatementsAndStableBatchOrdering() throws Exception {
        Configuration configuration = new Configuration();
        parse(configuration, "mapper/PermissionMapper.xml");
        parse(configuration, "mapper/RolePermissionMapper.xml");

        assertThat(configuration.hasStatement(
                "com.jacolp.system.infrastructure.persistence.mapper.PermissionMapper.selectByCode")).isTrue();
        assertThat(configuration.hasStatement(
                "com.jacolp.system.infrastructure.persistence.mapper.PermissionMapper.selectActiveByRoleIds")).isTrue();
        assertThat(configuration.hasStatement(
                "com.jacolp.system.infrastructure.persistence.mapper.RolePermissionMapper.selectByRoleIds")).isTrue();

        assertThat(content("mapper/PermissionMapper.xml"))
                .contains("p.status = 'active'")
                .contains("SELECT DISTINCT")
                .contains("ORDER BY p.code ASC, p.id ASC");
        assertThat(content("mapper/RolePermissionMapper.xml"))
                .contains("ORDER BY role_id ASC, perm_id ASC")
                .contains("#{roleId}")
                .contains("#{permId}");
    }

    @Test
    void permissionAdapterUsesOneSortedBatchQueryForDirectActiveRolePermissions() {
        PermissionMapper mapper = mock(PermissionMapper.class);
        MyBatisPermissionMetadataRepository repository = new MyBatisPermissionMetadataRepository(mapper);
        PermissionDO permissionDataObject = permissionDataObject();
        PermissionMetadata permissionMetadata = permissionMetadata();
        when(mapper.selectActiveByRoleIds(List.of(1L, 3L))).thenReturn(List.of(permissionDataObject));

        Assertions.assertThat(repository.findActiveByRoleIds(Arrays.asList(3L, null, 1L, 3L)))
                .containsExactly(permissionMetadata);
        verify(mapper).selectActiveByRoleIds(List.of(1L, 3L));

        Assertions.assertThat(repository.findActiveByRoleIds(List.of())).isEmpty();
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void permissionAdapterDelegatesCatalogueQueriesAndWritesWithoutPermissionCalculation() {
        PermissionMapper mapper = mock(PermissionMapper.class);
        MyBatisPermissionMetadataRepository repository = new MyBatisPermissionMetadataRepository(mapper);
        PermissionDO permissionDataObject = permissionDataObject();
        PermissionMetadata permissionMetadata = permissionMetadata();
        when(mapper.selectByCode("note:read")).thenReturn(permissionDataObject);
        when(mapper.insert(any(PermissionDO.class))).thenReturn(1);
        when(mapper.updateById(any(PermissionDO.class))).thenReturn(1);
        when(mapper.deleteById(7L)).thenReturn(1);

        Assertions.assertThat(repository.findByCode("note:read")).contains(permissionMetadata);
        assertThat(repository.insert(permissionMetadata)).isEqualTo(1);
        assertThat(repository.updateById(permissionMetadata)).isEqualTo(1);
        assertThat(repository.deleteById(7L)).isEqualTo(1);
        verify(mapper).selectByCode("note:read");
        verify(mapper).insert(permissionDataObject);
        verify(mapper).updateById(permissionDataObject);
        verify(mapper).deleteById(7L);
    }

    @Test
    void rolePermissionAdapterUsesOneSortedBatchQueryAndDelegatesRelationWrites() {
        RolePermissionMapper mapper = mock(RolePermissionMapper.class);
        MyBatisRolePermissionMetadataRepository repository = new MyBatisRolePermissionMetadataRepository(mapper);
        RolePermissionDO rolePermissionDataObject = rolePermissionDataObject();
        RolePermissionMetadata rolePermissionMetadata = rolePermissionMetadata();
        when(mapper.selectByRoleIds(List.of(1L, 2L))).thenReturn(List.of(rolePermissionDataObject));
        when(mapper.insert(any(RolePermissionDO.class))).thenReturn(1);
        when(mapper.deleteByRoleIdAndPermId(2L, 3L)).thenReturn(1);

        Assertions.assertThat(repository.findByRoleIds(List.of(2L, 1L, 2L))).containsExactly(rolePermissionMetadata);
        assertThat(repository.insert(rolePermissionMetadata)).isEqualTo(1);
        assertThat(repository.deleteByRoleIdAndPermId(2L, 3L)).isEqualTo(1);
        verify(mapper).selectByRoleIds(List.of(1L, 2L));
        verify(mapper).insert(rolePermissionDataObject);
        verify(mapper).deleteByRoleIdAndPermId(2L, 3L);
    }

    private static void parse(Configuration configuration, String resourcePath) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream inputStream = resource.getInputStream()) {
            new XMLMapperBuilder(inputStream, configuration, resource.getDescription(), configuration.getSqlFragments())
                    .parse();
        }
    }

    private static String content(String resourcePath) throws Exception {
        return new ClassPathResource(resourcePath).getContentAsString(StandardCharsets.UTF_8);
    }

    private static void assertNoInfrastructurePersistenceType(Class<?> portType) {
        for (Method method : portType.getDeclaredMethods()) {
            assertThat(method.getGenericReturnType().getTypeName()).doesNotContain(".infrastructure.persistence.");
            for (java.lang.reflect.Type parameterType : method.getGenericParameterTypes()) {
                assertThat(parameterType.getTypeName()).doesNotContain(".infrastructure.persistence.");
            }
        }
    }

    private static PermissionDO permissionDataObject() {
        PermissionDO permission = new PermissionDO();
        permission.setId(7L);
        permission.setCode("note:read");
        permission.setOauthScope("legacy-note-read");
        permission.setResource("note");
        permission.setAction("read");
        permission.setStatus("active");
        permission.setDescription("Read notes");
        permission.setCreateTime(LocalDateTime.of(2026, 8, 10, 1, 2, 3));
        permission.setUpdateTime(LocalDateTime.of(2026, 8, 10, 1, 2, 4));
        return permission;
    }

    private static PermissionMetadata permissionMetadata() {
        PermissionDO permission = permissionDataObject();
        return new PermissionMetadata(permission.getId(), permission.getCode(), permission.getOauthScope(),
                permission.getResource(), permission.getAction(), permission.getStatus(), permission.getDescription(),
                permission.getCreateTime(), permission.getUpdateTime());
    }

    private static RolePermissionDO rolePermissionDataObject() {
        RolePermissionDO rolePermission = new RolePermissionDO();
        rolePermission.setRoleId(2L);
        rolePermission.setPermId(3L);
        rolePermission.setGrantTime(LocalDateTime.of(2026, 8, 10, 1, 2, 3));
        return rolePermission;
    }

    private static RolePermissionMetadata rolePermissionMetadata() {
        RolePermissionDO rolePermission = rolePermissionDataObject();
        return new RolePermissionMetadata(rolePermission.getRoleId(), rolePermission.getPermId(),
                rolePermission.getGrantTime());
    }
}
