package com.jacolp.middleware.module.system.biz.application.event;

import com.jacolp.middleware.messaging.event.EmailSendRequestedEvent;
import com.jacolp.module.system.biz.application.event.EmailSendRequestedEventHandler;
import com.jacolp.module.system.biz.infrastructure.email.SmtpEmailGateway;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailSendRequestedEventHandlerTest {

    @Test
    void delegatesTheSingleRetryUnitToSmtp() {
        SmtpEmailGateway smtp = mock(SmtpEmailGateway.class);
        EmailSendRequestedEvent request = new EmailSendRequestedEvent(
                "alice@example.com", "subject", "<p>body</p>", "CUSTOM", "custom:1");

        new EmailSendRequestedEventHandler(smtp).apply(request);

        verify(smtp).sendHtml("alice@example.com", "subject", "<p>body</p>");
    }
}
