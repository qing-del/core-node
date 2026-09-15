package com.jacolp.system.infrastructure.authorization;

import com.jacolp.system.application.authorization.model.EmailLoginCodeDeliveryRequest;
import com.jacolp.system.application.port.out.EmailLoginCodeDeliveryPort;
import com.jacolp.system.infrastructure.email.SmtpEmailGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Objects;

/** Synchronous SMTP delivery for a one-time email-login code. */
@Repository
public class SmtpEmailLoginCodeDeliveryAdapter implements EmailLoginCodeDeliveryPort {

    private static final String TEMPLATE = "email/login-code";
    private static final String SUBJECT = "CoreNode 登录验证码";

    private final TemplateEngine templateEngine;
    private final SmtpEmailGateway smtpEmailGateway;

    @Autowired
    public SmtpEmailLoginCodeDeliveryAdapter(TemplateEngine templateEngine, SmtpEmailGateway smtpEmailGateway) {
        this.templateEngine = Objects.requireNonNull(templateEngine, "templateEngine");
        this.smtpEmailGateway = Objects.requireNonNull(smtpEmailGateway, "smtpEmailGateway");
    }

    @Override
    public void deliver(EmailLoginCodeDeliveryRequest request) {
        Objects.requireNonNull(request, "request");
        Context context = new Context();
        context.setVariable("username", request.username());
        context.setVariable("verificationCode", request.rawCode());
        context.setVariable("expiryMinutes", request.ttl().toMinutes());
        String html = templateEngine.process(TEMPLATE, context);
        smtpEmailGateway.sendHtml(request.email(), SUBJECT, html);
    }
}
