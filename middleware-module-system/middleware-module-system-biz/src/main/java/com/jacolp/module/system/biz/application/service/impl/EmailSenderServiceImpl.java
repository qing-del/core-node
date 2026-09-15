package com.jacolp.module.system.biz.application.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.UserMapper;
import com.jacolp.middleware.common.security.token.TokenSessionService;
import com.jacolp.module.system.biz.application.service.EmailSenderService;
import com.jacolp.module.system.biz.application.dto.email.EmailSendDTO;
import com.jacolp.module.system.biz.application.dto.email.EmailResultDTO;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@Slf4j
public class EmailSenderServiceImpl implements EmailSenderService {
    // 依赖提供的 Bean
    @Autowired private JavaMailSender mailSender;
    @Autowired private TemplateEngine templateEngine;

    // 配置
    @Autowired private TokenSessionService tokenSessionService;

    // Mapper & Redis
    @Autowired private UserMapper userMapper;

    @Value("${jacolp.base-url}")
    private String baseUrl;

    @Value("${spring.mail.username}")
    private String from;

    @Override
    public String sendActivationEmail(UserDO user) {
        String token = tokenSessionService.issueActivationToken(user.getId());

        String activationUrl = normalizeBaseUrl(baseUrl) + "/activate/" + token;

        // 生成 6 位数字激活码并存入 Redis
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        tokenSessionService.saveActivationCode(code, user.getId());
        log.info("Activation code generated for user: {}", user.getId());

        // 设置邮件内容
        Context ctx = new Context();
        ctx.setVariable("username", user.getUsername());
        ctx.setVariable("activationUrl", activationUrl);
        ctx.setVariable("linkExpiryMinutes", tokenSessionService.activationLinkExpiryMinutes());
        ctx.setVariable("codeExpiryMinutes", tokenSessionService.activationCodeExpiryMinutes());
        ctx.setVariable("activationCode", code);
        // 渲染邮件内容
        String html = templateEngine.process("email/activation", ctx);

        // 发送邮件
        sendHtmlMail(user.getEmail(), "CoreNode 账号激活", html);
        log.info("Activation email sent to: {}", user.getEmail());
        return token;
    }

    @Override
    public EmailResultDTO sendCustomEmail(EmailSendDTO dto) {
        List<String> recipients = new ArrayList<>();

        // 根据用户 ID 查询收件人
        if (dto.getUserId() != null) {
            UserDO user = userMapper.selectById(dto.getUserId());
            if (user != null && user.getEmail() != null && !user.getEmail().isEmpty()) {
                recipients.add(user.getEmail());
            }
        }

        // 根据角色 ID 查询收件人
        if (dto.getRoleId() != null) {
            List<UserDO> users = userMapper.selectByRoleId(dto.getRoleId());
            for (UserDO u : users) {
                if (u.getEmail() != null && !u.getEmail().isEmpty()
                        && !recipients.contains(u.getEmail())) {
                    recipients.add(u.getEmail());
                }
            }
        }

        // 如果没有收件人，则返回
        if (recipients.isEmpty()) {
            return new EmailResultDTO(0, 0, "无有效收件人");
        }

        // 循环发送邮件
        int success = 0, fail = 0;
        for (String to : recipients) {
            try {
                if (dto.getTemplateName() != null && !dto.getTemplateName().isEmpty()) {
                    Context ctx = new Context();
                    ctx.setVariable("subject", dto.getSubject());
                    ctx.setVariable("body", dto.getBody());
                    String html = templateEngine.process("email/" + dto.getTemplateName(), ctx);
                    sendHtmlMail(to, dto.getSubject(), html);
                } else {
                    sendHtmlMail(to, dto.getSubject(), dto.getBody());
                }
                success++;
            } catch (Exception e) {
                log.error("Failed to send email to {}: {}", to, e.getMessage());
                fail++;
            }
        }

        return new EmailResultDTO(success, fail,
                String.format("发送完成：成功 %d，失败 %d", success, fail));
    }

    /**
     * 发送 HTML 邮件
     * @param to 收件人
     * @param subject 邮件主题
     * @param htmlContent HTML 内容
     */
    @Override
    public void sendHtmlMail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);   // 发件人
            helper.setTo(to);   // 收件人
            helper.setSubject(subject); // 邮件主题
            helper.setText(htmlContent, true);  // HTML 内容
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("邮件发送失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void sendEmailChangeCode(UserDO user, String newEmail) {
        // 生成 修改邮箱 使用的 6 位验证码
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));

        // 存储验证码到 Redis
        tokenSessionService.saveEmailChangeCode(code, user.getId(), newEmail);
        log.info("Email change code generated for user: {}, new email: {}", user.getId(), newEmail);

        // 构建邮件内容
        Context ctx = new Context();
        ctx.setVariable("username", user.getUsername());
        ctx.setVariable("newEmail", newEmail);
        ctx.setVariable("verificationCode", code);
        ctx.setVariable("expiryMinutes", tokenSessionService.emailChangeCodeExpiryMinutes());
        String html = templateEngine.process("email/email-change", ctx);

        // 发送 HTML 邮件
        sendHtmlMail(newEmail, "CoreNode 邮箱修改验证", html);
        log.info("Email change code sent to: {}", newEmail);
    }

    /**
     * 正常化 URL
     * @param url URL
     * @return 正常化的 URL
     */
    private String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
