package com.jacolp.system.application.aspect;

import com.jacolp.common.security.context.BaseContext;
import com.jacolp.common.core.exception.AuthenticationException;
import com.jacolp.system.application.annotation.RequireSuperiorRole;
import com.jacolp.system.application.authorization.RoleRankAuthorizationService;
import com.jacolp.system.application.provider.TargetUserProvider;
import com.jacolp.system.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.system.infrastructure.persistence.mapper.UserMapper;
import com.jacolp.system.exception.NotFindUserException;
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
 *   <li>操作者必须具有 CREATOR 或 ADMIN 管理身份</li>
 *   <li>操作者的 sys_role.rank 必须严格高于被操作者</li>
 * </ol>
 */
@Aspect
@Component
@Slf4j
public class SuperiorRoleAspect {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleRankAuthorizationService roleRankAuthorizationService;

    @Before("@annotation(com.jacolp.system.application.annotation.RequireSuperiorRole)")
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

        // 4. 管理身份按 role code，角色高低仅按 sys_role.rank。
        roleRankAuthorizationService.requireManagementRole(modifier.getRoleId());
        roleRankAuthorizationService.requireStrictlySuperior(modifier.getRoleId(), target.getRoleId());

        log.info("Role-rank check passed: modifier id={} -> target id={}", modifierId, targetUserId);
    }
}
