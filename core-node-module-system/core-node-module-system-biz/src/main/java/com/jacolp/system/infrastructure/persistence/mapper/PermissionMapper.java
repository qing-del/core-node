package com.jacolp.system.infrastructure.persistence.mapper;

import com.jacolp.system.infrastructure.persistence.dataobject.PermissionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PermissionMapper {

    PermissionDO selectByCode(@Param("code") String code);

    /**
     * Reads all active permissions granted directly to any supplied role in one query.
     */
    List<PermissionDO> selectActiveByRoleIds(@Param("roleIds") List<Long> roleIds);

    int insert(PermissionDO permission);

    int updateById(PermissionDO permission);

    int deleteById(@Param("id") Long id);
}
