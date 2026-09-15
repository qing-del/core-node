package com.jacolp.middleware.module.system.biz.application.service.impl;

import com.jacolp.common.security.activation.AccountVerificationCredentialService;
import com.jacolp.common.messaging.event.EmailSendRequestedEvent;
import com.jacolp.common.messaging.pulisher.EmailSendEventPublisher;
import com.jacolp.system.application.dto.email.EmailResultDTO;
import com.jacolp.system.application.dto.email.EmailSendDTO;
import com.jacolp.system.application.service.impl.EmailSenderServiceImpl;
import com.jacolp.system.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.system.infrastructure.persistence.mapper.UserMapper;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailSenderServiceImplTest {

    @Test
    void activationGeneratesCredentialsBeforeReliablyQueuingRenderedMail() {
        EmailSendEventPublisher publisher = mock(EmailSendEventPublisher.class);
        AccountVerificationCredentialService credentials = mock(AccountVerificationCredentialService.class);
        TemplateEngine templates = mock(TemplateEngine.class);
        EmailSenderServiceImpl service = service(publisher, credentials, templates, Mockito.mock(UserMapper.class));
        UserDO user = user(7L, "alice", "alice@example.com");
        when(credentials.issueActivationToken(7L)).thenReturn("opaque-token");
        when(credentials.activationLinkExpiryMinutes()).thenReturn(30L);
        when(credentials.activationCodeExpiryMinutes()).thenReturn(10L);
        when(templates.process(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn("<html>mail</html>");

        assertThat(service.sendActivationEmail(user)).isEqualTo("opaque-token");

        verify(credentials).saveActivationCode(anyString(), org.mockito.ArgumentMatchers.eq(7L));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EmailSendRequestedEvent>> requests = ArgumentCaptor.forClass(List.class);
        verify(publisher).publish(requests.capture());
        assertThat(requests.getValue()).containsExactly(new EmailSendRequestedEvent(
                "alice@example.com", "CoreNode 账号激活", "<html>mail</html>",
                "ACTIVATION", "activation:7"));
    }

    @Test
    void customBulkSendReturnsAcceptedCountInsteadOfBlockingForSmtpResults() {
        EmailSendEventPublisher publisher = mock(EmailSendEventPublisher.class);
        UserMapper users = mock(UserMapper.class);
        EmailSenderServiceImpl service = service(publisher, mock(AccountVerificationCredentialService.class),
                mock(TemplateEngine.class), users);
        EmailSendDTO dto = new EmailSendDTO();
        dto.setRoleId(2);
        dto.setSubject("Notice");
        dto.setBody("Body");
        when(users.selectByRoleId(2)).thenReturn(List.of(
                user(7L, "alice", "alice@example.com"),
                user(8L, "bob", "bob@example.com")));
        when(publisher.publish(org.mockito.ArgumentMatchers.anyList())).thenReturn(2);

        EmailResultDTO result = service.sendCustomEmail(dto);

        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailCount()).isZero();
        assertThat(result.getMessage()).contains("发送队列");
    }

    private static EmailSenderServiceImpl service(EmailSendEventPublisher publisher,
            AccountVerificationCredentialService credentials, TemplateEngine templates, UserMapper users) {
        EmailSenderServiceImpl service = new EmailSenderServiceImpl();
        ReflectionTestUtils.setField(service, "emailEventPublisher", publisher);
        ReflectionTestUtils.setField(service, "accountVerificationCredentialService", credentials);
        ReflectionTestUtils.setField(service, "templateEngine", templates);
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "baseUrl", "https://example.com/");
        return service;
    }

    private static UserDO user(long id, String username, String email) {
        UserDO user = new UserDO();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        return user;
    }
}
