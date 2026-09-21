package com.jacolp.system.infrastructure.authorization;

import com.jacolp.system.application.authorization.model.EmailLoginCodeDeliveryRequest;
import com.jacolp.system.infrastructure.email.SmtpEmailGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpEmailLoginCodeDeliveryAdapterTest {

    @Test
    void rendersTheDedicatedTemplateAndSendsItSynchronously() {
        TemplateEngine templates = mock(TemplateEngine.class);
        SmtpEmailGateway smtp = mock(SmtpEmailGateway.class);
        ArgumentCaptor<Context> context = ArgumentCaptor.forClass(Context.class);
        when(templates.process(eq("email/login-code"), context.capture())).thenReturn("<p>code</p>");

        new SmtpEmailLoginCodeDeliveryAdapter(templates, smtp).deliver(request());

        assertThat(context.getValue().getVariable("username")).isEqualTo("alice");
        assertThat(context.getValue().getVariable("verificationCode")).isEqualTo("012345");
        assertThat(context.getValue().getVariable("expiryMinutes")).isEqualTo(10L);
        verify(smtp).sendHtml("alice@example.test", "CoreNode 登录验证码", "<p>code</p>");
    }

    @Test
    void propagatesSmtpFailuresWithoutCatchingThem() {
        TemplateEngine templates = mock(TemplateEngine.class);
        SmtpEmailGateway smtp = mock(SmtpEmailGateway.class);
        when(templates.process(eq("email/login-code"), any(Context.class))).thenReturn("<p>code</p>");
        IllegalStateException failure = new IllegalStateException("SMTP delivery failed");
        doThrow(failure).when(smtp).sendHtml(any(), any(), any());

        assertThatThrownBy(() -> new SmtpEmailLoginCodeDeliveryAdapter(templates, smtp).deliver(request()))
                .isSameAs(failure);
    }

    @Test
    void productionCodeAndTemplateDoNotReuseOutboxOrLegacyEmailPaths() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/jacolp/system/infrastructure/authorization/SmtpEmailLoginCodeDeliveryAdapter.java"));
        String template = Files.readString(Path.of("src/main/resources/templates/email/login-code.html"));

        assertThat(source).contains("SmtpEmailGateway", "email/login-code", "sendHtml");
        assertThat(source).doesNotContain(
                "EmailSendEventPublisher", "Outbox", "EmailSenderService", "TokenSessionService",
                "activation", "email-change", "@Transactional");
        assertThat(template).contains("username", "verificationCode", "expiryMinutes");
        assertThat(template).doesNotContain("activation", "email-change", "newEmail", "activationCode");
    }

    private static EmailLoginCodeDeliveryRequest request() {
        return new EmailLoginCodeDeliveryRequest(
                "user",
                7L,
                "alice@example.test",
                "alice",
                "012345",
                Duration.ofMinutes(10));
    }
}
