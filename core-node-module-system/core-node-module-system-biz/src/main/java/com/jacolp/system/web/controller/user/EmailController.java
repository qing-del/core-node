package com.jacolp.system.web.controller.user;

import java.util.HashMap;
import java.util.Map;

import com.jacolp.common.core.constant.RateLimitConstant;
import com.jacolp.common.security.context.BaseContext;
import com.jacolp.common.core.result.Result;
import com.jacolp.system.application.dto.user.EmailChangeRequestDTO;
import com.jacolp.system.application.vo.user.UserDetailVO;
import com.jacolp.common.web.annotation.RateLimit;
import com.jacolp.system.application.service.UserUserService;
import com.jacolp.constant.UserConstant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("User-EmailController")
@RequestMapping("/user/email")
@Slf4j
@Schema(description = "User - 邮箱管理")
@Tag(name = "User-邮箱管理", description = "用户邮箱绑定与激活邮件重发接口")
public class EmailController {
    @Autowired private UserUserService userUserService;

    @PostMapping("/resend-activation")
    @Operation(summary = "重新发送激活邮件",
            description = "向当前用户注册邮箱再次发送激活链接")
    public Result<String> resendActivation() {
        Long userId = BaseContext.getCurrentId();
        log.info("User request resend activation email, userId: {}", userId);
        userUserService.sendActivationEmail(userId);
        return Result.success("激活邮件已重新发送，请查收邮箱");
    }

    @GetMapping("/status")
    @Operation(summary = "获取邮箱与激活状态",
            description = "返回当前用户的邮箱地址与账号激活状态")
    public Result<Map<String, Object>> getEmailStatus() {
        Long userId = BaseContext.getCurrentId();
        UserDetailVO user = userUserService.getCurrentUser();

        // 构造返回结果
        Map<String, Object> status = new HashMap<>();
        status.put("email", user.getEmail());   // 邮箱地址
        status.put("username", user.getUsername()); // 用户名
        boolean isActive = user.getStatus() != null && user.getStatus() == UserConstant.ACTIVE_STATUS;  // 账号激活状态
        status.put("isActive", isActive);   // 账号激活状态
        log.info("User email status query, userId: {}", userId);
        return Result.success(status);
    }

    @PostMapping("/change-code")
    @Operation(summary = "发起邮箱修改",
            description = "验证原邮箱后向新邮箱发送6位验证码")
    @RateLimit(windowSeconds = 60, maxRequests = 1, prefix = RateLimitConstant.EMAIL_RATE_LIMIT_KEY)
    @RateLimit(windowSeconds = 3600, maxRequests = 5, prefix = RateLimitConstant.EMAIL_RATE_LIMIT_KEY)
    public Result<String> sendEmailChangeCode(
            @Parameter(description = "原邮箱和新邮箱地址") @RequestBody @Valid EmailChangeRequestDTO dto) {
        log.info("User request email change, userId: {}", BaseContext.getCurrentId());
        userUserService.initiateEmailChange(dto);
        return Result.success("验证码已发送至新邮箱，请查收");
    }

    @PostMapping("/verify-change")
    @Operation(summary = "验证邮箱修改码",
            description = "使用新邮箱中收到的6位验证码完成邮箱修改")
    public Result<String> verifyEmailChange(
            @Parameter(description = "6位数字验证码，key 为 code")
            @RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return Result.error("验证码不能为空");
        }
        log.info("User verify email change code, userId: {}", BaseContext.getCurrentId());
        return Result.success(userUserService.verifyEmailChangeCode(code));
    }
}
