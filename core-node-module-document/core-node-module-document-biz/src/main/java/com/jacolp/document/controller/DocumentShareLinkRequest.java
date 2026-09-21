package com.jacolp.document.controller;

import com.jacolp.document.enums.DocumentPermission;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 创建文档分享短链时使用的请求体。 */
public record DocumentShareLinkRequest(
        /** 兑换后写入文档 ACL 的权限；WRITE 隐含 READ。 */
        @NotNull(message = "分享权限不能为空")
        DocumentPermission permission,
        /** 从创建时起计算的有效秒数。 */
        @NotNull(message = "有效时长不能为空")
        @Positive(message = "有效时长必须为正数")
        Long validForSeconds,
        /** 允许成功兑换的不同用户数量上限。 */
        @NotNull(message = "最大使用次数不能为空")
        @Positive(message = "最大使用次数必须为正数")
        Integer maxUses
) {
}
