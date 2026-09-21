package com.jacolp.system.web.controller.admin;

import com.jacolp.common.core.result.Result;
import com.jacolp.system.application.dto.email.EmailResultDTO;
import com.jacolp.system.application.dto.email.EmailSendDTO;
import com.jacolp.system.application.service.EmailSenderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("Admin-EmailController")
@RequestMapping("/admin/email")
@Slf4j
@Schema(description = "Admin - 邮件管理")
@Tag(name = "Admin-邮件管理", description = "管理员发送自定义邮件接口")
public class EmailController {
    @Autowired private EmailSenderService emailSenderService;

    @PostMapping("/send")
    @Operation(summary = "发送自定义邮件",
            description = "管理员可指定单个用户或按角色异步群发；返回值表示已可靠入队数量")
    public Result<EmailResultDTO> sendEmail(@RequestBody @Valid EmailSendDTO dto) {
        log.info("Admin send custom email, userId: {}, roleId: {}", dto.getUserId(), dto.getRoleId());
        return Result.success(emailSenderService.sendCustomEmail(dto));
    }
}
