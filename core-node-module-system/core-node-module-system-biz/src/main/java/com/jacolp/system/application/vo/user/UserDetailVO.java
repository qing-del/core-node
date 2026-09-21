package com.jacolp.system.application.vo.user;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户详情 VO —— 用于用户端获取自身信息时返回，不含密码等敏感字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    @Schema(description = "用户ID")
    private Long id;

    /** 登录用户名 */
    @Schema(description = "登录用户名")
    private String username;

    /** 用户昵称 */
    @Schema(description = "用户昵称")
    private String nickname;

    /** 邮箱地址 */
    @Schema(description = "邮箱地址")
    private String email;

    /** 角色ID：1-创建者，2-管理员，3-普通用户 */
    @Schema(description = "角色ID：1-创建者，2-管理员，3-普通用户")
    private Long roleId;

    /** 账号状态：2-未激活，1-正常，0-禁用，-1-已删除 */
    @Schema(description = "账号状态：2-未激活，1-正常，0-禁用，-1-已删除")
    private Integer status;

    /** 用户个性化最大存储空间(字节) */
    @Schema(description = "用户个性化最大存储空间(字节)")
    private Long maxStorageBytes;

    /** 用户当前已用存储空间(字节) */
    @Schema(description = "用户当前已用存储空间(字节)")
    private Long usedStorageBytes;

    /** 注册时间 */
    @Schema(description = "注册时间")
    private LocalDateTime createTime;
}
