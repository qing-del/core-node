package com.jacolp.system.application.service.impl;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.jacolp.common.security.context.BaseContext;
import com.jacolp.common.core.exception.AuthenticationException;
import com.jacolp.common.core.exception.BaseException;
import com.jacolp.common.core.exception.PermissionDeniedException;
import com.jacolp.common.messaging.event.UserProfileChangedEvent;
import com.jacolp.common.messaging.pulisher.UserProfileEventPublisher;
import com.jacolp.common.core.result.PageResult;
import com.jacolp.system.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.system.infrastructure.persistence.mapper.UserMapper;
import com.jacolp.common.core.utils.EmailUtil;
import com.jacolp.system.application.authorization.UserExtraGrantTypePolicy;
import com.jacolp.system.application.authorization.AccountAuthorizationStateRevocationService;
import com.jacolp.system.application.authorization.RoleRankAuthorizationService;
import com.jacolp.system.application.annotation.RequireValidRole;
import com.jacolp.system.application.dto.user.UserAddDTO;
import com.jacolp.system.application.dto.user.UserListDTO;
import com.jacolp.system.application.service.AdminUserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.system.infrastructure.security.PasswordEncoder;
import com.jacolp.system.constant.RoleConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.system.application.dto.user.UserModifyDTO;
import com.jacolp.system.application.dto.user.UserQuoteStorageDTO;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {
    private final AccountAuthorizationStateRevocationService authorizationStateRevocationService;

    public AdminUserServiceImpl(AccountAuthorizationStateRevocationService authorizationStateRevocationService) {
        this.authorizationStateRevocationService = Objects.requireNonNull(
                authorizationStateRevocationService, "authorizationStateRevocationService");
    }

    @Autowired private UserMapper userMapper;

    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserProfileEventPublisher userProfileEvents;
    @Autowired private RoleRankAuthorizationService roleRankAuthorizationService;

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
        if (dto.getRoleId() != null) {
            roleRankAuthorizationService.requireStrictlySuperior(modifier.getRoleId(), dto.getRoleId());
        }

        UserDO targetBeforeUpdate = userMapper.selectById(dto.getId());
        if (usernameChangeRequested(dto, targetBeforeUpdate)) {
            roleRankAuthorizationService.requireCreator(modifier.getRoleId());
        }

        // 构建更新实体，仅设置非空字段（updateById 的 XML 使用 <if> 动态判断）
        UserDO user = new UserDO();
        BeanUtils.copyProperties(dto, user);
        boolean securityFieldChangeRequested = securityFieldChangeRequested(dto, targetBeforeUpdate);
        if (dto.getRoleId() != null) {
            user.setExtraGrantTypes(UserExtraGrantTypePolicy.forRoleId(dto.getRoleId()));
        }

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
        if (securityFieldChangeRequested) {
            authorizationStateRevocationService.revokeForSecurityFieldChange(dto.getId());
        }
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

        // 3. Cannot assign a role equal to or higher than the modifier's own rank.
        if (dto.getRoleId() == null) {
            throw new PermissionDeniedException("权限不足：只能创建权限低于自身的账户");
        }
        roleRankAuthorizationService.requireStrictlySuperior(modifier.getRoleId(), dto.getRoleId());

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
        user.setExtraGrantTypes(UserExtraGrantTypePolicy.forRoleId(user.getRoleId()));
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
    @Transactional(rollbackFor = Exception.class)
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

        // 2. Management identity is fixed by role code; hierarchy is evaluated by rank below.
        roleRankAuthorizationService.requireManagementRole(modifier.getRoleId());

        // 3. Fail-fast: pre-fetch all targets and check for any privilege violation
        List<UserDO> targets = userMapper.selectByIds(ids);
        for (UserDO target : targets) {
            roleRankAuthorizationService.requireStrictlySuperior(modifier.getRoleId(), target.getRoleId());
        }

        // 4. Proceed with batch deletion
        int affected = userMapper.deleteByIds(ids);
        if (affected <= 0) {
            throw new BaseException(UserConstant.UPDATE_USER_INFO_FAILED);
        }
        for (Long targetId : distinctExistingIdsInRequestOrder(ids, targets)) {
            authorizationStateRevocationService.revokeForSecurityFieldChange(targetId);
        }
        log.info("Batch delete completed, count: {}", ids.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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

        authorizationStateRevocationService.revokeForSecurityFieldChange(targetId);
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

    private static List<Long> distinctExistingIdsInRequestOrder(List<Long> requestedIds, List<UserDO> targets) {
        Set<Long> existingIds = new HashSet<>();
        for (UserDO target : targets) {
            if (target != null && target.getId() != null) {
                existingIds.add(target.getId());
            }
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long requestedId : requestedIds) {
            if (requestedId != null && existingIds.contains(requestedId)) {
                distinctIds.add(requestedId);
            }
        }
        return List.copyOf(distinctIds);
    }

    private static boolean securityFieldChangeRequested(UserModifyDTO dto, UserDO targetBeforeUpdate) {
        if (StringUtils.hasText(dto.getNewPassword())) {
            return true;
        }
        if (targetBeforeUpdate == null) {
            return usernameChangeRequested(dto, null) || StringUtils.hasText(dto.getEmail())
                    || dto.getRoleId() != null || dto.getStatus() != null;
        }
        return usernameChangeRequested(dto, targetBeforeUpdate)
                || (StringUtils.hasText(dto.getEmail())
                && !Objects.equals(dto.getEmail(), targetBeforeUpdate.getEmail()))
                || (dto.getRoleId() != null && !Objects.equals(dto.getRoleId(), targetBeforeUpdate.getRoleId()))
                || (dto.getStatus() != null && !Objects.equals(dto.getStatus(), targetBeforeUpdate.getStatus()));
    }

    private static boolean usernameChangeRequested(UserModifyDTO dto, UserDO targetBeforeUpdate) {
        return StringUtils.hasText(dto.getUsername())
                && (targetBeforeUpdate == null || !Objects.equals(dto.getUsername(), targetBeforeUpdate.getUsername()));
    }
}
