package com.jacolp.system.web.controller.authorization;

import com.jacolp.common.core.result.Result;
import com.jacolp.system.application.authorization.EmailLoginCodeIssuanceService;
import com.jacolp.system.application.authorization.InternalAccountAuthenticationRejectedException;
import com.jacolp.system.application.authorization.model.EmailLoginCodeIssueRequest;
import com.jacolp.system.application.dto.authorization.EmailLoginCodeHttpRequest;
import com.jacolp.common.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OAuthEmailCodeControllerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ControllerOnlyConfiguration.class);

    @Test
    void declaresExactOauthEmailCodePostPath() throws NoSuchMethodException {
        RequestMapping requestMapping = OAuthEmailCodeController.class.getAnnotation(RequestMapping.class);
        PostMapping postMapping = OAuthEmailCodeController.class.getMethod(
                "issueEmailCode", EmailLoginCodeHttpRequest.class, jakarta.servlet.http.HttpServletRequest.class,
                jakarta.servlet.http.HttpServletResponse.class).getAnnotation(PostMapping.class);

        assertThat(requestMapping.value()).containsExactly("/oauth");
        assertThat(postMapping.value()).containsExactly("/email-code");
    }

    @Test
    void validRequestUsesDirectSocketRemoteAddressAndReturnsGenericSuccess() {
        EmailLoginCodeIssuanceService service = mock(EmailLoginCodeIssuanceService.class);
        OAuthEmailCodeController controller = new OAuthEmailCodeController(service);
        MockHttpServletRequest servletRequest = request("198.51.100.24");
        servletRequest.addHeader("X-Forwarded-For", "203.0.113.9");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        Result<Void> result = controller.issueEmailCode(
                new EmailLoginCodeHttpRequest("user", "Alice@Example.test"), servletRequest, servletResponse);

        ArgumentCaptor<EmailLoginCodeIssueRequest> captor = ArgumentCaptor.forClass(EmailLoginCodeIssueRequest.class);
        verify(service).issue(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new EmailLoginCodeIssueRequest("user", "Alice@Example.test", "198.51.100.24"));
        assertThat(servletResponse.getStatus()).isEqualTo(200);
        assertThat(result.getCode()).isEqualTo(Result.SUCCESS);
        assertThat(result.getMsg()).isEqualTo(OAuthEmailCodeController.SUCCESS_MESSAGE);
        assertThat(result.getData()).isNull();
    }

    @Test
    void invalidOrNullDtoReturnsBadRequestWithoutCallingService() {
        EmailLoginCodeIssuanceService service = mock(EmailLoginCodeIssuanceService.class);
        OAuthEmailCodeController controller = new OAuthEmailCodeController(service);

        for (EmailLoginCodeHttpRequest request : new EmailLoginCodeHttpRequest[]{
                null, new EmailLoginCodeHttpRequest("core_agent", "alice@example.test"),
                new EmailLoginCodeHttpRequest("user", " ")}) {
            MockHttpServletResponse servletResponse = new MockHttpServletResponse();

            Result<Void> result = controller.issueEmailCode(request, request("192.0.2.1"), servletResponse);

            assertThat(servletResponse.getStatus()).isEqualTo(400);
            assertThat(result.getCode()).isEqualTo(Result.FAIL);
            assertThat(result.getMsg()).isEqualTo(OAuthEmailCodeController.INVALID_REQUEST_MESSAGE);
        }
        verify(service, never()).issue(any());
    }

    @Test
    void serviceExceptionPropagatesWithoutChangingResponse() {
        EmailLoginCodeIssuanceService service = mock(EmailLoginCodeIssuanceService.class);
        OAuthEmailCodeController controller = new OAuthEmailCodeController(service);
        IllegalStateException failure = new IllegalStateException("redis unavailable");
        doThrow(failure).when(service).issue(any());
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller.issueEmailCode(
                new EmailLoginCodeHttpRequest("user", "alice@example.test"), request("192.0.2.1"), servletResponse))
                .isSameAs(failure);
        assertThat(servletResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void authenticationRejectionIsRenderedAsUnauthorizedWithReasonedMessage() throws Exception {
        EmailLoginCodeIssuanceService service = mock(EmailLoginCodeIssuanceService.class);
        doThrow(new InternalAccountAuthenticationRejectedException(
                InternalAccountAuthenticationRejectedException.Reason.ACCOUNT_NOT_FOUND))
                .when(service).issue(any());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OAuthEmailCodeController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/oauth/email-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_id\":\"user\",\"email\":\"alice@example.test\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(Result.FAIL))
                .andExpect(jsonPath("$.msg").value("邮箱未注册，无法发送验证码"));
    }

    @Test
    void registersController() {
        runner.withUserConfiguration(ServiceConfiguration.class)
                .run(context -> assertThat(context.getBeansOfType(OAuthEmailCodeController.class)).hasSize(1));
    }

    @Test
    void productionControllerDoesNotReferenceHeadersOutboxOrLegacyEmailFlows() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/jacolp/system/web/controller/authorization/"
                + "OAuthEmailCodeController.java"));

        assertThat(source).doesNotContain("getHeader", "X-Forwarded-For", "Forwarded", "Outbox",
                "EmailSendEventPublisher", "EmailSenderService", "TokenSessionService", "activation", "email-change",
                "Logger", "log.");
    }

    private static MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth/email-code");
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    @Configuration(proxyBeanMethods = false)
    @Import(OAuthEmailCodeController.class)
    static class ControllerOnlyConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class ServiceConfiguration {
        @Bean
        EmailLoginCodeIssuanceService issuanceService() {
            return mock(EmailLoginCodeIssuanceService.class);
        }
    }
}
