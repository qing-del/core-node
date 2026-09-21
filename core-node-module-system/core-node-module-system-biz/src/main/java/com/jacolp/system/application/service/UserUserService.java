package com.jacolp.system.application.service;

import com.jacolp.system.application.dto.user.EmailChangeRequestDTO;
import com.jacolp.system.application.dto.user.UserProfileUpdateDTO;
import com.jacolp.system.application.dto.user.UserRegisterDTO;
import com.jacolp.system.application.vo.user.UserDetailVO;
import com.jacolp.system.application.vo.user.UserOverviewVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface UserUserService {
    String register(@NotNull @Valid UserRegisterDTO userRegisterDTO);
    // 用户端：获取当前登录用户详情（不含密码）

    UserDetailVO getCurrentUser();
    // 用户端：获取当前登录用户概览信息（不含ID）

    UserOverviewVO getUserOverview();

    /** 用户端：软删除当前登录用户账户 */
    void deleteCurrentUser();

    /** 用户端：更新当前登录用户的个人资料 */
    void updateCurrentUserProfile(@NotNull @Valid UserProfileUpdateDTO dto);

    /**
     * 激活账户
     * @param userId 用户ID
     * @return 激活结果
     */
    String activeAccount(Long userId);

    /**
     * 发送激活邮件
     * <p>- 底层调用的方法带有{@code userId}的每分钟限制</p>
     * @param userId 用户ID
     */
    void sendActivationEmail(Long userId);

    /**
     * 匿名重发激活邮件
     * <p>- 底层调用的方法带有{@code userId}的每分钟限制</p>
     * @param account 用户名或邮箱
     */
    void sendActivationEmailByAccount(String account);

    /**
     * 通过 6 位激活码激活账号
     * @param code 6位数字激活码
     * @return 激活结果
     */
    String verifyActivationCode(String code);

    /** 发起邮箱更改：校验原邮箱后向新邮箱发送验证码 */
    void initiateEmailChange(@NotNull @Valid EmailChangeRequestDTO dto);

    /** 验证邮箱更改码并更新数据库中的邮箱 */
    String verifyEmailChangeCode(String code);
}
