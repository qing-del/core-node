package com.jacolp.document.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 在当前登录用户的个人空间中创建文档时使用的请求体。 */
public record DocumentCreateRequest(
        @NotBlank(message = "文档标题不能为空")
        @Size(max = 255, message = "文档标题不能超过 255 个字符")
        /** 新文档标题。<p>example: {@code 项目周会纪要}</p> */
        String title
) {
}
