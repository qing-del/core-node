package com.jacolp.module.system.biz.application.aspect;

import com.jacolp.constant.RoleConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.exception.AuthenticationException;
import com.jacolp.exception.NotFindUserException;
import com.jacolp.exception.PermissionDeniedException;
import com.jacolp.module.system.biz.application.annotation.RequireSuperiorRole;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.UserMapper;
import com.jacolp.module.system.biz.application.provider.TargetUserProvider;
import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.UserDO;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 切面：对标注了 {@link RequireSuperiorRole} 的方法做权限前置校验。
 * <p>
 * 校验规则：
 * <ol>
 *   <li>操作者（从 JWT → BaseContext 获取 id）的 roleId 必须 ≤ 2（创建者 / 管理员）</li>
 *   <li>操作者的 roleId 必须严格小于被操作者的 roleId</li>
 * </ol>
 */
@Aspect
@Component
@Slf4j
public class SuperiorRoleAspect {

    @Autowired
    private UserMapper userMapper;

    @Before("@annotation(com.jacolp.module.system.biz.application.annotation.RequireSuperiorRole)")
    public void checkSuperiorRole(JoinPoint joinPoint) {
        // 1. 从方法参数中查找 TargetUserProvider
        TargetUserProvider provider = null;
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof TargetUserProvider) {
                provider = (TargetUserProvider) arg;
                break;
            }
        }

        // Developer Error：方法签名中缺少 TargetUserProvider 参数
        if (provider == null) {
            throw new RuntimeException(
                    "[Developer Error] 标注了 @RequireSuperiorRole 的方法必须包含一个实现 TargetUserProvider 接口的参数。" +
                    "请检查方法: " + joinPoint.getSignature().toShortString());
        }

        // 2. 获取操作者 ID（JWT 拦截器已存入 BaseContext）
        Long modifierId = BaseContext.getCurrentId();
        Long targetUserId = provider.getTargetUserId();

        // 3. 查询操作者和被操作者的用户信息
        UserDO modifier = userMapper.selectById(modifierId);
        UserDO target = userMapper.selectById(targetUserId);

        if (modifier == null) {
            log.error("Modifier user not found, id: {}", modifierId);
            throw new AuthenticationException("操作者用户不存在");
        }
        if (target == null) {
            log.error("Target user not found, id: {}", targetUserId);
            throw new NotFindUserException("被操作的目标用户不存在");
        }

        // 4. 权限校验
        //    4a. 操作者必须是创建者或管理员（roleId <= 2）
        if (modifier.getRoleId() > RoleConstant.ADMIN) {
            log.error("Permission denied: Modifier roleId={} is not admin/creator", modifier.getRoleId());
            throw new PermissionDeniedException("权限不足：仅创建者和管理员可以修改其他用户");
        }

        //    4b. 操作者的 roleId 必须严格小于被操作者的 roleId
        if (modifier.getRoleId() >= target.getRoleId()) {
            log.error("Permission denied: Modifier roleId={} is not higher than target roleId={}", modifier.getRoleId(), target.getRoleId());
            throw new PermissionDeniedException("权限不足：只能修改权限低于自己的用户");
        }

        log.info("Role check passed: Modifier id={} (roleId={}) -> Target id={} (roleId={})",
                modifierId, modifier.getRoleId(), targetUserId, target.getRoleId());
    }
}
