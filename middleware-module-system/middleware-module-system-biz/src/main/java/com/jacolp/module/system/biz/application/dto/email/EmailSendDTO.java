package com.jacolp.module.system.biz.application.dto.email;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailSendDTO {
    private Long userId;
    private Integer roleId;

    @NotBlank(message = "邮件主题不能为空")
    private String subject;

    @NotBlank(message = "邮件内容不能为空")
    private String body;

    private String templateName;
}
