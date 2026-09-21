package com.jacolp.document.controller;

import com.jacolp.document.enums.DocumentPermission;
import jakarta.validation.constraints.NotNull;

/** 新增或更新文档用户授权时使用的请求体。 */
public record DocumentUserAuthorizationRequest(
        @NotNull(message = "文档权限不能为空")
        DocumentPermission permission,
        @NotNull(message = "授权状态不能为空")
        Boolean enabled
) {
}
