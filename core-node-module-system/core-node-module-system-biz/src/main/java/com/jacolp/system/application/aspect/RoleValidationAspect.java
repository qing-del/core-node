package com.jacolp.system.application.aspect;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.system.application.annotation.RequireValidRole;
import com.jacolp.system.application.provider.RoleIdProvider;
import com.jacolp.system.infrastructure.persistence.mapper.RoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class RoleValidationAspect {

    @Autowired
    private RoleMapper roleMapper;

    @Before("@annotation(requireValidRole)")
    public void validateRole(JoinPoint joinPoint, RequireValidRole requireValidRole) {
        // 1) 从参数中提取承载 roleId 的 DTO
        RoleIdProvider provider = null;
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof RoleIdProvider roleIdProvider) {
                provider = roleIdProvider;
                break;
            }
        }

        // 2) 开发期兜底：防止方法签名未按约定实现 RoleIdProvider
        if (provider == null) {
            throw new RuntimeException(
                    "[Developer Error] 标注了 @RequireValidRole 的方法必须包含一个实现 RoleIdProvider 接口的参数。"
                            + "请检查方法: " + joinPoint.getSignature().toShortString());
        }

        // 3) 读取角色 ID，按注解配置决定是否必填
        Long roleId = provider.getRoleId();
        if (roleId == null) {
            if (requireValidRole.required()) {
                throw new BaseException("角色不能为空");
            }
            // 更新场景未传 roleId 时，允许直接跳过角色存在性校验
            return;
        }

        // 4) 基础数值校验，避免无意义数据库查询
        if (roleId <= 0) {
            throw new BaseException("角色不存在");
        }

        // 5) 角色存在性校验
        Integer roleCount = roleMapper.countById(roleId);
        if (roleCount == null || roleCount <= 0) {
            log.error("Role not exists, roleId={}", roleId);
            throw new BaseException("角色不存在");
        }
    }
}
