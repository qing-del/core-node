package com.jacolp.module.system.biz.application.event;

import com.jacolp.middleware.messaging.event.EmailSendRequestedEvent;
import com.jacolp.module.system.biz.infrastructure.email.SmtpEmailGateway;
import org.springframework.stereotype.Service;

@Service
public class EmailSendRequestedEventHandler {
    public static final String CONSUMER_NAME = "system.email-send";

    private final SmtpEmailGateway smtpEmailGateway;

    public EmailSendRequestedEventHandler(SmtpEmailGateway smtpEmailGateway) {
        this.smtpEmailGateway = smtpEmailGateway;
    }

    public void apply(EmailSendRequestedEvent request) {
        smtpEmailGateway.sendHtml(request.recipient(), request.subject(), request.htmlContent());
    }
}
