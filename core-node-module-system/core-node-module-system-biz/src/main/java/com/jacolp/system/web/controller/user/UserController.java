package com.jacolp.system.web.controller.user;

import java.util.Map;

import com.jacolp.common.core.constant.RateLimitConstant;
import com.jacolp.common.security.context.BaseContext;
import com.jacolp.common.core.result.Result;
import com.jacolp.system.application.dto.user.UserProfileUpdateDTO;
import com.jacolp.system.application.dto.user.UserRegisterDTO;
import com.jacolp.system.application.vo.user.UserDetailVO;
import com.jacolp.system.application.vo.user.UserOverviewVO;
import com.jacolp.common.web.annotation.RateLimit;
import com.jacolp.system.application.service.UserUserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@RestController("User-UserController")
@RequestMapping("/user/user")
@Slf4j
@Schema(description = "User - 用户管理")
@Validated
@Tag(name = "User-用户认证", description = "用户注册与登录接口")
public class UserController {
    @Autowired private UserUserService userUserService;

    @PostMapping("/register")
    @Operation(summary = "用户注册",
            description = "创建普通用户账号并发送邮箱激活邮件；账号激活前无法登录使用。")
    public Result<String> register(
            @Parameter(description = "用户注册请求，包含用户名、密码和基本信息") @RequestBody @Valid UserRegisterDTO userRegisterDTO) {
        log.info("User register: {}", userRegisterDTO.getUsername());
        return Result.success(userUserService.register(userRegisterDTO));
    }

    @PostMapping("/resend-activation")
    @Operation(summary = "匿名重发激活邮件",
            description = "未激活用户可通过用户名或邮箱重新发送激活邮件，无需登录")
    @RateLimit(windowSeconds = 60, maxRequests = 1, prefix = RateLimitConstant.EMAIL_RATE_LIMIT_KEY)
    @RateLimit(windowSeconds = 3600, maxRequests = 5, prefix = RateLimitConstant.EMAIL_RATE_LIMIT_KEY)
    public Result<String> resendActivation(
            @Parameter(description = "用户名或邮箱，key 为 account")
            @RequestBody Map<String, String> body) {
        String account = body.get("account");
        if (account == null || account.isBlank()) {
            return Result.error("用户名或邮箱不能为空");
        }
        log.info("User request anonymous activation email resend");
        userUserService.sendActivationEmailByAccount(account);
        return Result.success("激活邮件已重新发送，请查收邮箱");
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息",
            description = "从 JWT 中解析当前用户ID，查询并返回用户详情（不含密码等敏感字段）。")
    public Result<UserDetailVO> getCurrentUser() {
        log.info("User get current user info");
        return Result.success(userUserService.getCurrentUser());
    }

    @GetMapping("/overview")
    @Operation(summary = "获取用户概览",
            description = "返回当前用户的基本信息，不包含资源统计数据。")
    public Result<UserOverviewVO> getOverview() {
        log.info("User get overview");
        return Result.success(userUserService.getUserOverview());
    }

    @PutMapping("/me")
    @Operation(summary = "更新当前用户信息",
            description = "仅允许修改当前登录用户自身的昵称、邮箱等资料字段；修改密码可以复用这个接口")
    public Result<String> updateCurrentUser(
            @Parameter(description = "用户资料更新请求（昵称、邮箱等可修改字段）") @RequestBody @Valid UserProfileUpdateDTO dto) {
        log.info("User update profile");
        userUserService.updateCurrentUserProfile(dto);
        return Result.success("更新成功");
    }

    @DeleteMapping("/me")
    @Operation(summary = "删除账户（软删除）",
            description = "将当前登录用户账户状态更新为软删除状态，保留历史数据，避免物理删除造成关联记录丢失。")
    public Result<String> deleteCurrentUser() {
        log.info("User soft-delete account");
        userUserService.deleteCurrentUser();
        return Result.success("账户已删除");
    }

    /**
     * 用户激活
     * @param token 激活码
     * @return 激活结果
     */
    @RequestMapping("/active/{token}")
    @Operation(summary = "用户激活",
            description = "用户注册成功后，会通过邮件发送激活链接，点击链接后调用该接口完成用户激活。")
    public Result<String> active(@PathVariable String token) {
        if (token == null || token.isBlank()) {
            return Result.error("激活码不能为空");
        }
        log.info("User active, userId: {}, token: {}", BaseContext.getCurrentId(), token);
        return Result.success(userUserService.activeAccount(BaseContext.getCurrentId()));
    }

    @PostMapping("/active-code")
    @Operation(summary = "通过激活码激活账号",
            description = "通过邮箱中收到的 6 位数字激活码完成账号激活，无需 JWT 令牌")
    @RateLimit(windowSeconds = 60, maxRequests = 1, prefix = RateLimitConstant.EMAIL_RATE_LIMIT_KEY)
    @RateLimit(windowSeconds = 3600, maxRequests = 5, prefix = RateLimitConstant.EMAIL_RATE_LIMIT_KEY)
    public Result<String> activeByCode(
            @Parameter(description = "6位数字激活码，key 为 code")
            @RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return Result.error("激活码不能为空");
        }
        log.info("User active by code: {}", code);
        return Result.success(userUserService.verifyActivationCode(code));
    }
}
