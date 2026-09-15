package com.jacolp.module.system.biz.infrastructure.persistence.mapper;

import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.RoleDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper {

    @Select("select COUNT(1) from sys_role where id = #{id}")
    Integer countById(Long id);

    @Select("select max_storage_bytes from sys_role where id = #{id}")
    Long selectMaxStorageById(Long id);

    @Select("select * from sys_role where id = #{id}")
    RoleDO getById(long user);

    @Select("select * from sys_role")
    List<RoleDO> getAll();
}
