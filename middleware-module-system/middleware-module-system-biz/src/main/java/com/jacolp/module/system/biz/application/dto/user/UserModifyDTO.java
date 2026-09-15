package com.jacolp.module.system.biz.application.dto.user;

import com.jacolp.constant.UserConstant;
import com.jacolp.module.system.biz.application.provider.RoleIdProvider;
import com.jacolp.module.system.biz.application.provider.TargetUserProvider;
import com.jacolp.module.system.biz.application.provider.UsernameAndPasswordProvider;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员修改用户信息的请求参数。
 * 除 createTime / updateTime 之外包含 UserEntity 的所有可修改字段，
 * 另外额外携带 newPassword / confirmPassword 用于密码覆盖。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserModifyDTO implements TargetUserProvider, RoleIdProvider, UsernameAndPasswordProvider {

    @NotNull(message = "用户ID不能为空")
    @Positive(message = "用户ID必须为正数")
    private Long id;

    @Size(min = UserConstant.USERNAME_MIN_LENGTH, max = UserConstant.USERNAME_MAX_LENGTH, message = "用户名长度必须在 4 - 50 之间")
    @Pattern(
            regexp = "^[a-zA-Z0-9_]+$",
            message = "用户名只能包含字母、数字和下划线"
    )
    private String username;

    @Size(min = UserConstant.USERNAME_MIN_LENGTH, max = UserConstant.USERNAME_MAX_LENGTH, message = "用户名长度必须在 4 - 50 之间")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Positive(message = "角色ID必须为正数")
    private Long roleId;

    private Integer status;

    @Size(min = UserConstant.PASSWORD_MIN_LENGTH, max = UserConstant.PASSWORD_MAX_LENGTH, message = "密码长度必须在 6 - 60 之间")
    private String newPassword;

    /** 确认密码（必须与 newPassword 一致） */
    private String confirmPassword;

    /** 用户最大存储空间(字节)，仅管理员可设置 */
    private Long maxStorageBytes;

    @Override
    public Long getTargetUserId() {
        return this.id;
    }

    @Override
    public String getPassword() {
        return this.newPassword;
    }
}
