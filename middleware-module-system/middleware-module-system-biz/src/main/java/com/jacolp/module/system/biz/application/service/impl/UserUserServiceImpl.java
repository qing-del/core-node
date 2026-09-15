package com.jacolp.module.system.biz.application.service.impl;

import com.jacolp.module.system.biz.infrastructure.security.PasswordEncoder;
import com.jacolp.constant.RoleConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.exception.BaseException;
import com.jacolp.exception.NotFindUserException;
import com.jacolp.exception.PasswordIncorrectException;
import com.jacolp.exception.UserIsBanException;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.UserMapper;
import com.jacolp.module.system.biz.application.dto.user.EmailChangeRequestDTO;
import com.jacolp.module.system.biz.application.dto.user.UserLoginDTO;
import com.jacolp.module.system.biz.application.dto.user.UserProfileUpdateDTO;
import com.jacolp.module.system.biz.application.dto.user.UserRegisterDTO;
import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.module.system.biz.application.vo.user.UserDetailVO;
import com.jacolp.module.system.biz.application.vo.user.UserOverviewVO;
import com.jacolp.middleware.common.security.token.TokenSessionService;
import com.jacolp.middleware.messaging.event.UserProfileChangedEvent;
import com.jacolp.middleware.messaging.pulisher.UserProfileEventPublisher;
import com.jacolp.module.system.biz.application.service.EmailSenderService;
import com.jacolp.module.system.biz.application.service.UserUserService;
import com.jacolp.utils.EmailUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Service
@Slf4j
@Validated
public class UserUserServiceImpl implements UserUserService {
    @Autowired private UserMapper userMapper;

    @Autowired private TokenSessionService tokenSessionService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmailSenderService emailSenderService;
    @Autowired private UserProfileEventPublisher userProfileEvents;

    @Override
    public String loginUser(@NotNull @Valid UserLoginDTO userLoginDTO) {
        // 1. 根据用户名查用户
        UserDO user = userMapper.selectByUsername(userLoginDTO.getUsername());
        if (user == null) {
            log.error("User isn't existed!");
            throw new NotFindUserException(UserConstant.NOT_FOUND_USER);
        }

        // 2.1 账号被禁用则直接拒绝登录
        if (user.getStatus() == UserConstant.BANNED_STATUS) {
            log.error("User is banned!");
            throw new UserIsBanException(UserConstant.USER_IS_BANNED);
        }

        // 2.2 账号未激活则拒绝登录
        if (user.getStatus() == UserConstant.UNACTIVE_STATUS) {
            log.error("User account is not activated, userId: {}", user.getId());
            throw new BaseException(UserConstant.ACCOUNT_NOT_ACTIVATED);
        }

        // 3. 校验密码是否一致
        boolean valid = passwordEncoder.matches(userLoginDTO.getPassword(), user.getPassword());
        if (!valid) {
            log.error("Password isn't correct!");
            throw new PasswordIncorrectException(UserConstant.USER_PASSWORD_ERROR);
        }

        // 将用户ID写入 JWT，后续请求会通过拦截器解析出来
        return tokenSessionService.issueUserLoginToken(user.getId());
    }

    @Override
    public void logout() {
        tokenSessionService.revokeUserLoginToken(BaseContext.getCurrentId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String register(@NotNull @Valid UserRegisterDTO userRegisterDTO) {
        // 校验两次密码一致性
        if (!userRegisterDTO.getPassword().equals(userRegisterDTO.getConfirmPassword())) {
            throw new BaseException(UserConstant.PASSWORD_CONFIRM_ERROR);
        }

        // 检查是否存在相同用户名的用户
        UserDO existed = userMapper.selectByUsername(userRegisterDTO.getUsername());
        if (existed != null) {
            throw new BaseException(UserConstant.USER_ALREADY_EXISTS);
        }

        // 构建用户实体类（注册用户）
        UserDO user = new UserDO();
        user.setUsername(userRegisterDTO.getUsername());
        user.setPassword(passwordEncoder.encode(userRegisterDTO.getPassword()));
        user.setNickname(userRegisterDTO.getUsername());    // 默认昵称为用户名
        user.setEmail(userRegisterDTO.getEmail());  // 设置邮箱
        user.setRoleId(RoleConstant.USER);
        user.setStatus(UserConstant.UNACTIVE_STATUS);   // 默认用户状态为未激活
        user.setMaxStorageBytes(RoleConstant.USER_MAX_STORAGE_BYTES);   // 默认用户存储空间为 100MB

        // 插入用户
        int count = userMapper.insertUser(user);
        if (count <= 0) {
            throw new BaseException("注册失败");
        }

        publishProfile(user);
        sendActivationEmail(user.getId());
        return "注册成功，请查收邮箱激活账号";
    }

    /**
     * 获取当前登录用户详情（不含密码）
     * @return 当前登录用户详情
     */
    @Override
    public UserDetailVO getCurrentUser() {
        Long userId = BaseContext.getCurrentId();
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFindUserException(UserConstant.NOT_FOUND_USER);
        }

        // 转换为 VO，排除密码等敏感字段
        UserDetailVO vo = new UserDetailVO();
        BeanUtils.copyProperties(user, vo);
        vo.setId(null);
        if (vo.getNickname() == null || vo.getNickname().isEmpty()) {
            vo.setNickname(vo.getUsername());
        }
        return vo;
    }

    /**
     * 获取当前登录用户概览信息（不含用户ID）。
     */
    @Override
    public UserOverviewVO getUserOverview() {
        Long userId = BaseContext.getCurrentId();
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFindUserException(UserConstant.NOT_FOUND_USER);
        }

        UserOverviewVO vo = new UserOverviewVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @Override
    public void deleteCurrentUser() {
        Long userId = BaseContext.getCurrentId();
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFindUserException(UserConstant.NOT_FOUND_USER);
        }

        // 软删除：仅更新状态为已删除，保留历史数据
        UserDO updateEntity = new UserDO();
        updateEntity.setId(userId);
        updateEntity.setStatus(UserConstant.DELETED_STATUS);
        int affected = userMapper.updateById(updateEntity);
        if (affected <= 0) {
            log.error("User soft-delete failed, userId: {}", userId);
            throw new BaseException("用户删除失败");
        }

        log.info("User soft-deleted (account deactivated), userId: {}", userId);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCurrentUserProfile(@NotNull @Valid UserProfileUpdateDTO dto) {
        Long userId = BaseContext.getCurrentId();
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFindUserException(UserConstant.NOT_FOUND_USER);
        }

        // 检验是否需要更改名称
        if (StringUtils.hasText(dto.getNickname())) {
            user.setNickname(dto.getNickname());
        }
        // 检验是否需要更改邮箱
        if (StringUtils.hasText(dto.getEmail())) {
            if (user.getStatus() == UserConstant.ACTIVE_STATUS) {
                throw new BaseException(UserConstant.EMAIL_CHANGE_DIRECT_NOT_ALLOWED);
            }
            user.setEmail(dto.getEmail());
        }

        // 检验是否需要更改密码
        if (StringUtils.hasText(dto.getPassword())) {
            if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
                throw new BaseException(UserConstant.USER_PASSWORD_ERROR);
            }
            if (StringUtils.hasText(dto.getConfirmPassword())
                    || !dto.getNewPassword().equals(dto.getConfirmPassword())) {
                throw new BaseException(UserConstant.PASSWORD_CONFIRM_ERROR);
            }
            user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        }

        // 更新数据库
        int affected = userMapper.updateById(user);
        if (affected <= 0) {
            log.error("User profile update failed, userId: {}", userId);
            throw new BaseException(UserConstant.UPDATE_USER_INFO_FAILED);
        }
        publishProfile(user);
        log.info("User profile updated, userId: {}", userId);
    }

    private void publishProfile(UserDO user) {
        userProfileEvents.publish(new UserProfileChangedEvent(
                user.getId(), user.getUsername(), user.getNickname()));
    }

    /**
     * 用户激活
     * @param userId 用户ID
     * @return 激活结果
     */
    @Override
    public String activeAccount(Long userId) {
        log.info("User active: {}", userId);

        // 查询用户
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            log.error("User not found, userId: {}", userId);
            throw new NotFindUserException(UserConstant.NOT_FOUND_USER);
        }

        // 检查是否出现重复激活
        if (user.getStatus() != UserConstant.UNACTIVE_STATUS) {
            log.error("User status is active, not reconditioning, userId: {}", user.getId());
            throw new BaseException(UserConstant.USER_ALREADY_ACTIVE);
        }

        // 更新数据库
        user.setStatus(UserConstant.ACTIVE_STATUS);
        int affected = userMapper.updateById(user);
        if (affected <= 0) {
            log.error("User active failed, userId: {}", user.getId());
            throw new BaseException(UserConstant.UPDATE_USER_INFO_FAILED);
        }
        return "激活成功";
    }

    /**
     * 发送激活邮件
     * <p>- 底层调用的方法带有{@code userId}的每分钟限制</p>
     * @param userId 用户ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendActivationEmail(Long userId) {
        UserDO user = userMapper.selectById(userId);
        sendActivationEmail(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendActivationEmailByAccount(String account) {
        if (!StringUtils.hasText(account)) {
            throw new BaseException("用户名或邮箱不能为空");
        }

        // 去除空白字符
        String trimmedAccount = account.trim();

        // 尝试通过用户名或邮箱查询用户
        UserDO user = EmailUtil.isValidEmail(trimmedAccount)
                ? userMapper.selectByEmail(trimmedAccount)
                : userMapper.selectByUsername(trimmedAccount);

        if (user == null) {
            log.error("User not found by account: {}", trimmedAccount);
            throw new NotFindUserException(UserConstant.NOT_FOUND_USER);
        }

        sendActivationEmail(user);
    }

    /**
     * 发送激活邮件
     * <p>- 会通过 {@code Redis} 限制每分钟内单个用户只能收到一次验证码</p>
     * @param user 用户信息
     */
    private void sendActivationEmail(UserDO user) {

        // 检查用户是否存在
        if (user == null) {
            log.error("User not found when sending activation email");
            throw new NotFindUserException(UserConstant.NOT_FOUND_USER);
        }

        // 检查账号是否处于未激活状态
        if (user.getStatus() != UserConstant.UNACTIVE_STATUS) {
            throw new BaseException(UserConstant.USER_ALREADY_ACTIVE);
        }

        // 检查是否存在邮箱
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            log.error("User email is empty, userId: {}", user.getId());
            throw new BaseException(UserConstant.USER_EMAIL_IS_EMPTY);
        }

        // 校验邮箱格式
        if (!EmailUtil.isValidEmail(user.getEmail())) {
            log.error("Invalid email format for userId: {}", user.getId());
            throw new BaseException(UserConstant.INVALID_EMAIL_FORMAT);
        }

        // 尝试使用 Redis 做用户速率限制
        if (!tokenSessionService.acquireActivationEmailCooldown(user.getId())) {
            throw new BaseException(UserConstant.ACTIVATION_EMAIL_SEND_TOO_FREQUENT);
        }

        // 发送邮件
        emailSenderService.sendActivationEmail(user);
        log.info("Activation email sent to: {}", user.getEmail());
    }

    @Override
    public String verifyActivationCode(String code) {
        Long userId = tokenSessionService.findActivationCodeUserId(code);
        if (userId == null) {
            throw new BaseException("激活码无效或已过期");
        }
        String result = activeAccount(userId);
        tokenSessionService.deleteActivationCode(code);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initiateEmailChange(@NotNull @Valid EmailChangeRequestDTO dto) {
        Long userId = BaseContext.getCurrentId();
        UserDO user = userMapper.selectById(userId);

        // 检查用户是否存在
        if (user == null) {
            throw new NotFindUserException(UserConstant.NOT_FOUND_USER);
        }

        // 检查账号是否处于激活状态（激活了才可以修改邮箱）
        if (user.getStatus() != UserConstant.ACTIVE_STATUS) {
            throw new BaseException(UserConstant.ACCOUNT_NOT_ACTIVATED);
        }

        // 检查旧邮箱
        if (!dto.getOldEmail().equals(user.getEmail())) {
            throw new BaseException(UserConstant.OLD_EMAIL_NOT_MATCH);
        }

        // 检查邮箱是否合法 & 是否被支持
        if (!EmailUtil.isValidEmail(dto.getNewEmail())) {
            throw new BaseException(UserConstant.EMAIL_CHANGE_SEND_FAILED);
        }
        if (!EmailUtil.isSupportedEmail(dto.getNewEmail())) {
            throw new BaseException(UserConstant.EMAIL_CHANGE_SEND_FAILED);
        }

        try {
            // 发送修改邮箱的 6 位验证码邮件
            emailSenderService.sendEmailChangeCode(user, dto.getNewEmail());
        } catch (Exception e) {
            log.error("Failed to queue email change code for userId: {}", userId, e);
            throw new BaseException(UserConstant.EMAIL_CHANGE_SEND_FAILED);
        }
    }

    @Override
    public String verifyEmailChangeCode(String code) {
        TokenSessionService.EmailChangeCode stored = tokenSessionService.findEmailChangeCode(code);
        if (stored == null) {
            throw new BaseException("验证码无效或已过期");
        }
        Long userId = stored.userId();
        String newEmail = stored.newEmail();

        Long currentUserId = BaseContext.getCurrentId();
        if (!userId.equals(currentUserId)) {
            log.warn("Email change code mismatch: code owner={}, current user={}", userId, currentUserId);
            throw new BaseException("验证码无效或已过期");
        }

        UserDO updateEntity = new UserDO();
        updateEntity.setId(userId);
        updateEntity.setEmail(newEmail);
        int affected = userMapper.updateById(updateEntity);
        if (affected <= 0) {
            log.error("Email change DB update failed, userId: {}", userId);
            throw new BaseException(UserConstant.UPDATE_USER_INFO_FAILED);
        }

        tokenSessionService.deleteEmailChangeCode(code);
        log.info("Email changed successfully for userId: {}", userId);
        return "邮箱修改成功";
    }
}
