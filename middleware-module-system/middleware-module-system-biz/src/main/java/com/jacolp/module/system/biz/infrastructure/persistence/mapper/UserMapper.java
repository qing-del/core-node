package com.jacolp.module.system.biz.infrastructure.persistence.mapper;

import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.module.system.biz.application.dto.user.UserListDTO;
import com.jacolp.module.system.biz.application.dto.user.UserQuoteStorageDTO;
import com.jacolp.module.system.biz.application.dto.user.UserStorageHandlerDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("select * from sys_user where id = #{id}")
    UserDO selectById(Long id);

    @Select("select * from sys_user where username = #{username}")
    UserDO selectByUsername(String username);

    @Select("select * from sys_user where email = #{email}")
    UserDO selectByEmail(String email);

    @Select("select id, username, nickname, email, role_id, status from sys_user where role_id = #{roleId}")
    List<UserDO> selectByRoleId(Integer roleId);

    int upsertCreator(UserDO user);

    int updateById(UserDO user);

    int insertUser(UserDO user);

    int deleteByIds(@Param("ids") List<Long> ids);

    List<UserDO> selectByIds(@Param("ids") List<Long> ids);

    List<UserDO> listByCondition(UserListDTO userListDTO);

    @Select("select role_id, max_storage_bytes, used_storage_bytes as usedStorageBytes from sys_user where id = #{id}")
    UserQuoteStorageDTO selectQuoteStorageById(Long id);

    /** 批量查询用户存储信息。 */
    List<UserQuoteStorageDTO> selectQuoteStorageByIds(@Param("ids") List<Long> userIds);

    /** 批量更新用户已用存储量。 */
    int batchUpdateStorage(@Param("users") List<UserStorageHandlerDTO> users);

    /** 批量插入或更新用户，不修改密码。 */
    int upsertUser(List<UserDO> users);

    /** 按增量更新用户已用存储量，CAS 失败时返回 0。 */
    int updateStorageById(@Param("updateUser") UserStorageHandlerDTO updateUser);

    @Update("UPDATE sys_user SET used_storage_bytes = used_storage_bytes - #{amountBytes}, " +
            "update_time = NOW() WHERE id = #{userId} AND used_storage_bytes >= #{amountBytes}")
    int releaseStorageIfSufficient(@Param("userId") long userId, @Param("amountBytes") long amountBytes);

    /** 更新用户最大存储字节数。 */
    int updateMaxStorageById(Long id, Long maxStorageBytes);

    /** 批量查询用户数量。 */
    int countByIds(List<Long> userIds);
}
