package com.jacolp.module.system.biz.application.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.jacolp.middleware.messaging.pulisher.EmailSendEventPublisher;
import com.jacolp.middleware.messaging.event.EmailSendRequestedEvent;
import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.UserMapper;
import com.jacolp.middleware.common.security.token.TokenSessionService;
import com.jacolp.module.system.biz.application.service.EmailSenderService;
import com.jacolp.module.system.biz.application.dto.email.EmailSendDTO;
import com.jacolp.module.system.biz.application.dto.email.EmailResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class EmailSenderServiceImpl implements EmailSenderService {
    @Autowired private TemplateEngine templateEngine;
    @Autowired private EmailSendEventPublisher emailEventPublisher;

    // 配置
    @Autowired private TokenSessionService tokenSessionService;

    // Mapper & Redis
    @Autowired private UserMapper userMapper;

    @Value("${jacolp.base-url}")
    private String baseUrl;

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

        emailEventPublisher.publish(List.of(new EmailSendRequestedEvent(user.getEmail(),
                "CoreNode 账号激活", html, "ACTIVATION", "activation:" + user.getId())));
        log.info("Activation email queued for userId: {}", user.getId());
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

        List<EmailSendRequestedEvent> requests = new ArrayList<>();
        for (String to : recipients) {
            String html = dto.getBody();
            if (dto.getTemplateName() != null && !dto.getTemplateName().isEmpty()) {
                Context ctx = new Context();
                ctx.setVariable("subject", dto.getSubject());
                ctx.setVariable("body", dto.getBody());
                html = templateEngine.process("email/" + dto.getTemplateName(), ctx);
            }
            requests.add(new EmailSendRequestedEvent(to, dto.getSubject(), html,
                    "CUSTOM", "custom:" + UUID.randomUUID()));
        }
        int queued = emailEventPublisher.publish(requests);
        return new EmailResultDTO(queued, 0, String.format("已进入发送队列：%d 封", queued));
    }

    @Override
    public void sendEmailChangeCode(UserDO user, String newEmail) {
        // 生成 修改邮箱 使用的 6 位验证码
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));

        // 存储验证码到 Redis
        tokenSessionService.saveEmailChangeCode(code, user.getId(), newEmail);
        log.info("Email change code generated for userId: {}", user.getId());

        // 构建邮件内容
        Context ctx = new Context();
        ctx.setVariable("username", user.getUsername());
        ctx.setVariable("newEmail", newEmail);
        ctx.setVariable("verificationCode", code);
        ctx.setVariable("expiryMinutes", tokenSessionService.emailChangeCodeExpiryMinutes());
        String html = templateEngine.process("email/email-change", ctx);

        emailEventPublisher.publish(List.of(new EmailSendRequestedEvent(newEmail,
                "CoreNode 邮箱修改验证", html, "EMAIL_CHANGE", "email-change:" + user.getId())));
        log.info("Email change code queued for userId: {}", user.getId());
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
