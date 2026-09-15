package com.jacolp.system.web.controller.admin;

import com.jacolp.common.security.context.BaseContext;
import com.jacolp.common.core.result.PageResult;
import com.jacolp.common.core.result.Result;
import com.jacolp.system.application.dto.user.UserAddDTO;
import com.jacolp.system.application.dto.user.UserListDTO;
import com.jacolp.system.application.dto.user.UserModifyDTO;
import com.jacolp.system.application.dto.user.UserStatusDTO;
import com.jacolp.system.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.system.application.annotation.RequireSuperiorRole;
import com.jacolp.system.application.service.AdminUserService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("Admin-UserController")
@RequestMapping("/admin/user")
@Slf4j
@Schema(description = "Admin - 用户管理")
@Tag(name = "Admin-用户管理", description = "管理员登录、用户增删改查与封禁/解封接口")
public class UserController {
    @Autowired private AdminUserService adminUserService;

    @PostMapping("/list")
    @Operation(summary = "分页查询用户列表", description = "按用户名、角色等条件分页查询用户列表，返回分页结果供管理端展示。")
    public Result<PageResult> list(
            @Parameter(description = "用户列表查询条件，包含分页参数和筛选字段") @RequestBody UserListDTO userListDTO) {
        return Result.success(adminUserService.list(userListDTO));
    }

    @PutMapping("/user")
    @Operation(summary = "修改用户信息", description = "修改指定用户的基本信息、权限或密码，需要上级角色权限。")
    @RequireSuperiorRole
    public Result<String> modify(
            @Parameter(description = "用户修改请求，包含目标用户ID和需要修改的字段") @RequestBody @Valid UserModifyDTO userModifyDTO) {
        log.info("Admin modify user, target id: {}", userModifyDTO.getId());
        adminUserService.modifyUser(userModifyDTO);
        return Result.success();
    }

    @PostMapping("/user")
    @Operation(summary = "管理员新增账户", description = "管理员直接创建新用户账户，无需注册流程。")
    public Result<String> add(
            @Parameter(description = "新增用户请求，包含用户名、密码和角色信息") @RequestBody @Valid UserAddDTO userAddDTO) {
        log.info("Admin add user, username: {}", userAddDTO.getUsername());
        adminUserService.addUser(userAddDTO);
        return Result.success();
    }

    @DeleteMapping("/user")
    @Operation(summary = "批量删除账户", description = "按用户ID列表批量删除用户账户。")
    public Result<String> delete(
            @Parameter(description = "待删除的用户ID列表") @RequestParam List<Long> ids) {
        log.info("Admin delete users, ids: {}", ids);
        adminUserService.deleteUsers(ids);
        return Result.success();
    }

    @PostMapping("/status/{status}")
    @Operation(summary = "封禁/解封账号", description = "设置用户账号的启用/禁用状态，需要上级角色权限。")
    @RequireSuperiorRole
    public Result<String> updateStatus(
            @Parameter(description = "目标状态（1:启用, 0:禁用）") @PathVariable Integer status,
            @Parameter(description = "包含目标用户ID的请求体") @RequestBody UserStatusDTO userStatusDTO) {
        log.info("Admin update user status, target id: {}, status: {}", userStatusDTO.getId(), status);
        adminUserService.updateStatus(userStatusDTO.getId(), status);
        return Result.success();
    }

    @GetMapping("/user")
    @Operation(summary = "获取用户信息", description = "根据用户ID获取单个用户的详细信息。")
    public Result<UserDO> get(
            @Parameter(description = "用户ID") @RequestParam Long id) {
        log.info("Admin get user, id: {}", id);
        return Result.success(adminUserService.getUserById(id));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息",
            description = "从 JWT 中解析当前用户ID，查询并返回用户详情（不含密码等敏感字段）。")
    public Result<UserDO> getCurrentUser() {
        log.info("Admin get current user info, userId: {}", BaseContext.getCurrentId());
        return Result.success(adminUserService.getUserById(BaseContext.getCurrentId()));
    }
}

