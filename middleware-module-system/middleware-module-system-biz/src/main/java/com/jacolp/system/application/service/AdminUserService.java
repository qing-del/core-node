package com.jacolp.system.application.service;

import java.util.List;

import com.jacolp.common.core.result.PageResult;
import com.jacolp.system.application.dto.user.UserAddDTO;
import com.jacolp.system.application.dto.user.UserListDTO;
import com.jacolp.system.application.dto.user.UserModifyDTO;
import com.jacolp.system.application.dto.user.UserQuoteStorageDTO;
import com.jacolp.system.infrastructure.persistence.dataobject.UserDO;

public interface AdminUserService {
    PageResult list(UserListDTO userListDTO);
    // 管理员修改用户信息（权限/密码/基本信息）

    void modifyUser(UserModifyDTO userModifyDTO);
    // 管理员新增账户（只能赋予低于自身的角色）

    void addUser(UserAddDTO userAddDTO);
    // 管理员批量删除账户（含权限一票否决制）

    void deleteUsers(List<Long> ids);
    // 管理员封禁/解封账号状态

    void updateStatus(Long targetId, Integer status);
    // 获取用户信息

    UserDO getUserById(Long id);

    UserQuoteStorageDTO getUserQuoteStorage(Long userId);


    /** 被切面类借用的方法 */
    void updateUserStorageUsed(Long userId, Long usedStorageBytes);
}
