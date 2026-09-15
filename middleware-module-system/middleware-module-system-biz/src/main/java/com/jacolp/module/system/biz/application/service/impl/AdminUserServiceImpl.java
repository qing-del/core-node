package com.jacolp.module.system.biz.application.service.impl;

import java.util.List;

import com.jacolp.middleware.common.security.token.TokenSessionService;
import com.jacolp.middleware.messaging.event.UserProfileChangedEvent;
import com.jacolp.middleware.messaging.pulisher.UserProfileEventPublisher;
import com.jacolp.module.system.biz.application.annotation.RequireValidRole;
import com.jacolp.module.system.biz.application.dto.user.UserAddDTO;
import com.jacolp.module.system.biz.application.dto.user.UserListDTO;
import com.jacolp.module.system.biz.application.dto.user.UserLoginDTO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.module.system.biz.infrastructure.security.PasswordEncoder;
import com.jacolp.constant.RoleConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.exception.AuthenticationException;
import com.jacolp.exception.BaseException;
import com.jacolp.exception.NotFindUserException;
import com.jacolp.exception.PasswordIncorrectException;
import com.jacolp.exception.PermissionDeniedException;
import com.jacolp.exception.UserIsBanException;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.UserMapper;
import com.jacolp.module.system.biz.application.dto.user.UserModifyDTO;
import com.jacolp.module.system.biz.application.dto.user.UserQuoteStorageDTO;
import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.result.PageResult;
import com.jacolp.module.system.biz.application.service.AdminUserService;
import com.jacolp.utils.EmailUtil;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {
    @Autowired private UserMapper userMapper;

    @Autowired private TokenSessionService tokenSessionService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserProfileEventPublisher userProfileEvents;

    @Override
    public String loginAdmin(UserLoginDTO userLoginDTO) {
        // 通过用户名查询用户
        UserDO user = userMapper.selectByUsername(userLoginDTO.getUsername());
        if (user == null) {
            log.error("User not found!");
            throw new NotFindUserException(UserConstant.NOT_FOUND_USER);
        }

        // 检查账号状态
        if (user.getStatus() == UserConstant.BANNED_STATUS) {
            log.error("User is banned!");
            throw new UserIsBanException(UserConstant.USER_IS_BANNED);
        }

        // 检查账号权限
        if (user.getRoleId() != RoleConstant.ADMIN
                && user.getRoleId() != RoleConstant.CREATOR) {
            log.error("User isn't admin!");
            throw new PasswordIncorrectException(UserConstant.PERMISSION_DENIED);
        }

        // 检查密码
        if (!passwordEncoder.matches(userLoginDTO.getPassword(), user.getPassword())) {
            log.error("Password isn't correct!");
            throw new PasswordIncorrectException(UserConstant.USER_PASSWORD_ERROR);
        }

        // 生成 JWT 令牌（封装 id 到令牌里面）
        return tokenSessionService.issueAdminLoginToken(user.getId());
    }

    @Override
    public void logout() {
        tokenSessionService.revokeAdminLoginToken(BaseContext.getCurrentId());
    }

    @Override
    public PageResult list(UserListDTO userListDTO) {
        // 如果没有传递参数则为查询全表
        if (userListDTO == null) {
            userListDTO = new UserListDTO();
        }

        PageHelper.startPage(userListDTO.getPageNumOrDefault(), userListDTO.getPageSizeOrDefault());
        List<UserDO> records = userMapper.listByCondition(userListDTO);
        PageInfo<UserDO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    @Override
    // 修改用户时 roleId 可选：未传则不做角色存在性校验，传了才校验
    @RequireValidRole(required = false)
    @Transactional(rollbackFor = Exception.class)
    public void modifyUser(UserModifyDTO dto) {
        log.info("Admin modifying user, target user id: {}", dto.getId());

        // 检查权限
        UserDO modifier = userMapper.selectById(BaseContext.getCurrentId());
        if (modifier == null) {
            throw new AuthenticationException("操作者用户不存在");
        }
        if (dto.getRoleId() != null && modifier.getRoleId() >= dto.getRoleId()) {
            log.error("Permission denied: Modifier roleId={} cannot assign roleId={}",
                    modifier.getRoleId(), dto.getRoleId());
            throw new BaseException("权限不足：只能改动权限低于自身的账户");
        }

        // 构建更新实体，仅设置非空字段（updateById 的 XML 使用 <if> 动态判断）
        UserDO user = new UserDO();
        BeanUtils.copyProperties(dto, user);

        // 处理密码修改：无需旧密码，直接覆盖
        if (StringUtils.hasText(dto.getNewPassword())) {
            // 校验两次输入是否一致
            if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
                throw new BaseException(UserConstant.PASSWORD_CONFIRM_ERROR);
            }
            user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        }

        // 处理邮箱修改：如果提供了新邮箱，则校验格式
        if (StringUtils.hasText(dto.getEmail())) {
            if (!EmailUtil.isValidEmail(dto.getEmail())) {
                throw new BaseException(UserConstant.INVALID_EMAIL_FORMAT);
            }
        }

        int affected = userMapper.updateById(user);
        if (affected <= 0) {
            throw new BaseException(UserConstant.UPDATE_USER_INFO_FAILED);
        }
        publishProfile(userMapper.selectById(dto.getId()));
    }

    @Override
    // 新增用户必须指定有效角色
    @RequireValidRole
    @Transactional(rollbackFor = Exception.class)
    public void addUser(UserAddDTO dto) {
        log.info("Admin adding new user, username: {}, roleId: {}", dto.getUsername(), dto.getRoleId());

        // 1. Get the modifier's identity
        Long modifierId = BaseContext.getCurrentId();
        UserDO modifier = userMapper.selectById(modifierId);
        if (modifier == null) {
            throw new AuthenticationException("操作者用户不存在");
        }

        // 3. Cannot assign a role equal to or higher than the modifier's own role
        if (dto.getRoleId() == null || dto.getRoleId() <= modifier.getRoleId()) {
            log.error("Permission denied: Modifier roleId={} cannot assign roleId={}",
                    modifier.getRoleId(), dto.getRoleId());
            throw new PermissionDeniedException("权限不足：只能创建权限低于自身的账户");
        }

        // 2. Check if the username already exists
        UserDO existed = userMapper.selectByUsername(dto.getUsername());
        if (existed != null) {
            throw new BaseException(UserConstant.USER_ALREADY_EXISTS);
        }

        // 5. Build and persist the new user
        UserDO user = new UserDO();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setRoleId(dto.getRoleId());
        Integer status = dto.getStatus();
        user.setStatus(status != null ? status : UserConstant.ACTIVE_STATUS);
        user.setMaxStorageBytes(dto.getMaxStorageBytes() != null
                ? dto.getMaxStorageBytes() : RoleConstant.USER_MAX_STORAGE_BYTES);

        int rows = userMapper.insertUser(user);
        if (rows != 1) {
            throw new BaseException("新增用户失败");
        }
        publishProfile(user);
        log.info("User created successfully, username: {}", dto.getUsername());
    }

    @Override
    public void deleteUsers(List<Long> ids) {
        log.info("Admin deleting users, ids: {}", ids);

        if (ids == null || ids.isEmpty()) {
            throw new BaseException("待删除的用户 ID 列表不能为空");
        }

        // 1. Check modifier identity
        Long modifierId = BaseContext.getCurrentId();
        UserDO modifier = userMapper.selectById(modifierId);
        if (modifier == null) {
            throw new AuthenticationException("操作者用户不存在");
        }

        // 2. Modifier must be admin or creator (roleId <= 2)
        if (modifier.getRoleId() > RoleConstant.ADMIN) {
            log.error("Permission denied: Modifier roleId={} is not admin/creator", modifier.getRoleId());
            throw new PermissionDeniedException("权限不足：仅创建者和管理员可以删除账户");
        }

        // 3. Fail-fast: pre-fetch all targets and check for any privilege violation
        List<UserDO> targets = userMapper.selectByIds(ids);
        for (UserDO target : targets) {
            if (target.getRoleId() <= modifier.getRoleId()) {
                log.error("Permission denied: Cannot delete user id={} (roleId={}), modifier roleId={}",
                        target.getId(), target.getRoleId(), modifier.getRoleId());
                throw new PermissionDeniedException(
                        "权限不足：id=" + target.getId() + " 的用户权限不低于操作者，已终止整批删除");
            }
        }

        // 4. Proceed with batch deletion
        userMapper.deleteByIds(ids);
        log.info("Batch delete completed, count: {}", ids.size());
    }

    @Override
    public void updateStatus(Long targetId, Integer status) {
        log.info("Admin updating user status, target id: {}, new status: {}", targetId, status);

        // Validate status value
        if (status == null || (status != 0 && status != 1)) {
            throw new BaseException("无效的状态值，只接受 0（封禁）或 1（正常）");
        }

        // The AOP @RequireSuperiorRole on the controller already handled role verification.
        // Just build a minimal update entity and persist.
        UserDO user = new UserDO();
        user.setId(targetId);
        user.setStatus(status);
        int affected = userMapper.updateById(user);
        if (affected <= 0) {
            log.error("User profile update failed, user: {}", user);
            throw new BaseException(UserConstant.UPDATE_USER_INFO_FAILED);
        }

        log.info("User status updated successfully, id: {}, status: {}", targetId, status);
    }

    @Override
    public UserDO getUserById(Long id) {
        if (id == null || id <= 0) {
            log.error("Invalid user id: {}", id);
            throw new BaseException("无效的用户 ID");
        }
        UserDO user = userMapper.selectById(id);
        user.setPassword(null);
        if (user.getNickname() == null) {
            user.setNickname(user.getUsername());
        }
        return user;
    }

    @Override
    public UserQuoteStorageDTO getUserQuoteStorage(Long userId) {
        return userMapper.selectQuoteStorageById(userId);
    }

    @Override
    public void updateUserStorageUsed(Long userId, Long usedStorageBytes) {
        UserDO user = new UserDO();
        user.setId(userId);
        user.setUsedStorageBytes(usedStorageBytes);
        userMapper.updateById(user);
    }

    private void publishProfile(UserDO user) {
        if (user == null) {
            throw new BaseException(UserConstant.NOT_FOUND_USER);
        }
        userProfileEvents.publish(new UserProfileChangedEvent(
                user.getId(), user.getUsername(), user.getNickname()));
    }
}
