package com.jacolp.module.system.biz.application.dto.user;

import com.jacolp.constant.UserConstant;
import com.jacolp.module.system.biz.application.provider.RoleIdProvider;
import com.jacolp.module.system.biz.application.provider.UsernameAndPasswordProvider;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin 端新增用户请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAddDTO implements RoleIdProvider, UsernameAndPasswordProvider {

    @NotBlank(message = "用户名不能为空")
    @Size(min = UserConstant.USERNAME_MIN_LENGTH, max = UserConstant.USERNAME_MAX_LENGTH, message = "用户名长度必须在 4 - 50 之间")
    @Pattern(
            regexp = "^[a-zA-Z0-9_]+$",
            message = "用户名只能包含字母、数字和下划线"
    )
    private String username;

    @Size(min = UserConstant.PASSWORD_MIN_LENGTH, max = UserConstant.PASSWORD_MAX_LENGTH, message = "密码长度必须在 6 - 60 之间")
    private String password;

    @Positive(message = "角色ID必须为正数")
    private Long roleId;

    @Size(min = UserConstant.USERNAME_MIN_LENGTH, max = UserConstant.USERNAME_MAX_LENGTH, message = "用户名长度必须在 4 - 50 之间")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    /** 账号状态（选填，默认1正常启用） */
    private Integer status;

    /** 用户最大存储空间(字节)，留空则默认 100MB */
    private Long maxStorageBytes;
}
