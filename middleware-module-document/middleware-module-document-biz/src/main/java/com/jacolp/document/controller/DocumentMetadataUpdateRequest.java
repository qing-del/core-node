package com.jacolp.document.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** v0.3 允许客户端修改的文档元数据请求体。 */
public record DocumentMetadataUpdateRequest(
        @NotBlank(message = "文档标题不能为空")
        @Size(max = 255, message = "文档标题不能超过 255 个字符")
        /** 要更新的文档标题。<p>example: {@code 2026 年产品规划}</p> */
        String title
) {
}
