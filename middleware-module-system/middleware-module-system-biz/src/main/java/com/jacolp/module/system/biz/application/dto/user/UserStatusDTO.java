package com.jacolp.module.system.biz.application.dto.user;

import com.jacolp.module.system.biz.application.provider.TargetUserProvider;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 封禁/解封账号的请求参数。
 * 实现 {@link TargetUserProvider} 以复用 {@code @RequireSuperiorRole} AOP 权限鉴权切面。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusDTO implements TargetUserProvider {

    @NotBlank(message = "用户ID不能为空")
    private Long id;

    @Override
    public Long getTargetUserId() {
        return this.id;
    }
}
