package com.jacolp.system.application.dto.user;

import java.io.Serializable;

import com.jacolp.constant.UserConstant;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户端更新自身信息的请求 DTO。
 * 昵称、邮箱为可选字段；
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Size(min = UserConstant.USERNAME_MIN_LENGTH, max = UserConstant.USERNAME_MAX_LENGTH, message = "用户名长度必须在 4 - 50 之间")
    private String nickname;

    @Email
    private String email;

    /* ==== 以下是修改密码三件套 ==== */
    @Size(min = UserConstant.PASSWORD_MIN_LENGTH, max = UserConstant.PASSWORD_MAX_LENGTH, message = "密码长度必须在 6 - 60 之间")
    private String password;

    @Size(min = UserConstant.PASSWORD_MIN_LENGTH, max = UserConstant.PASSWORD_MAX_LENGTH, message = "密码长度必须在 6 - 60 之间")
    private String newPassword;

    /** 确认密码（可选） */
    private String confirmPassword;
}
