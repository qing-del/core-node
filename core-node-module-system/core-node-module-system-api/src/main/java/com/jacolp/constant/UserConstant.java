package com.jacolp.constant;

public class UserConstant {
    /**
     * 用户名、密码长度限制
     */
    public static final int USERNAME_MIN_LENGTH = 4;
    public static final int USERNAME_MAX_LENGTH = 50;
    public static final int PASSWORD_MIN_LENGTH = 6;
    public static final int PASSWORD_MAX_LENGTH = 60;

    /**
     * 用户状态
     */
    public static final int DELETED_STATUS = -1;
    public static final int UNACTIVE_STATUS = 2;
    public static final int ACTIVE_STATUS = 1;
    public static final int BANNED_STATUS = 0;

    /**
     * 激活用户账号状态使用的常量
     */
    public static final String JWT_NOT_VALID = "无效的激活链接";

    public static final String USER_LOGIN_FAILED = "用户登录失败";
    public static final String NOT_FOUND_USER = "用户不存在";
    public static final String USER_IS_BANNED = "用户已封禁";
    public static final String USER_PASSWORD_ERROR = "用户密码错误";
    public static final String PERMISSION_DENIED = "无权限";
    public static final String USERNAME_IS_REQUIRED = "用户名不能为空";
    public static final String PASSWORD_IS_REQUIRED = "密码不能为空";
    public static final String PASSWORD_CONFIRM_ERROR = "两次密码输入不一致";
    public static final String USER_ALREADY_EXISTS = "用户名已存在";
    public static final String UPDATE_USER_STORAGE_FAILED = "更新用户存储空间失败";
    public static final String USERNAME_AND_PASSWORD_PROVIDER_ERROR = "校验用户密码时发生错误";
    public static final String USER_ALREADY_ACTIVE = "用户已激活";
    public static final String UPDATE_USER_INFO_FAILED = "更新用户信息失败";
    public static final String USER_EMAIL_IS_EMPTY = "用户邮箱不能为空";
    public static final String EMAIL_NOT_PROVIDED = "邮箱未提供";
    public static final String INVALID_EMAIL_FORMAT = "邮箱格式不正确";
    public static final String UNSUPPORTED_EMAIL_DOMAIN = "不支持的邮箱域名";
    public static final String MAX_STORAGE_LIMIT = "存储空间已满";

    /**
     * 邮箱修改验证码常量
     */
    public static final String EMAIL_CHANGE_SEND_FAILED = "邮箱地址错误";
    public static final String OLD_EMAIL_NOT_MATCH = "原邮箱地址与当前绑定邮箱不匹配";
    public static final String EMAIL_CHANGE_DIRECT_NOT_ALLOWED = "已激活账号请通过邮箱验证流程修改邮箱";
    public static final String ACCOUNT_NOT_ACTIVATED = "账号未激活，请先激活账号";
    public static final String ACTIVATION_EMAIL_SEND_FAILED = "激活邮件发送失败，请稍后重试或使用重发激活邮件";
    public static final String ACTIVATION_EMAIL_SEND_TOO_FREQUENT = "请求过于频繁，请稍后再试";
}
