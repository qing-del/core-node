package com.jacolp.system.web.controller.authorization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.common.core.result.Result;
import com.jacolp.system.application.authorization.InternalAccountAuthenticationRejectedException;
import com.jacolp.system.application.authorization.InternalLoginService;
import com.jacolp.system.application.authorization.InternalRefreshTokenRejectedException;
import com.jacolp.system.application.authorization.model.InternalIssuedTokens;
import com.jacolp.system.application.authorization.model.InternalLoginRequest;
import com.jacolp.system.application.dto.authorization.InternalLoginHttpRequest;
import com.jacolp.system.application.dto.authorization.InternalTokenHttpResponse;
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
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalAuthControllerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ControllerOnlyConfiguration.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void declaresExactAuthLoginPostPath() throws NoSuchMethodException {
        RequestMapping requestMapping = InternalAuthController.class.getAnnotation(RequestMapping.class);
        PostMapping postMapping = InternalAuthController.class.getMethod(
                "login", InternalLoginHttpRequest.class, jakarta.servlet.http.HttpServletRequest.class,
                jakarta.servlet.http.HttpServletResponse.class).getAnnotation(PostMapping.class);

        assertThat(requestMapping.value()).containsExactly("/auth");
        assertThat(postMapping.value()).containsExactly("/login");
    }

    @Test
    void passwordLoginUsesDirectSocketAddressAndReturnsFiveStandardTokenFields() throws Exception {
        InternalLoginService service = mock(InternalLoginService.class);
        InternalAuthController controller = new InternalAuthController(service);
        when(service.login(any())).thenReturn(tokens());
        MockHttpServletRequest servletRequest = request("198.51.100.24");
        servletRequest.addHeader("X-Forwarded-For", "203.0.113.9");

        Result<InternalTokenHttpResponse> result = controller.login(
                new InternalLoginHttpRequest("user", "password", "alice", "secret", null, null, "note:read"),
                servletRequest, new MockHttpServletResponse());

        ArgumentCaptor<InternalLoginRequest> captor = ArgumentCaptor.forClass(InternalLoginRequest.class);
        verify(service).login(captor.capture());
        InternalLoginRequest domain = captor.getValue();
        assertThat(domain.clientId()).isEqualTo("user");
        assertThat(domain.grantType()).isEqualTo("password");
        assertThat(domain.username()).isEqualTo("alice");
        assertThat(domain.socketRemoteAddress()).isEqualTo("198.51.100.24");
        assertThat(result.getCode()).isEqualTo(Result.SUCCESS);
        assertThat(result.getData().toString()).doesNotContain("access.raw.token", "refresh.raw.token", "note:read");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(result.getData()));
        assertThat(json.fieldNames()).toIterable()
                .containsExactly("access_token", "token_type", "expires_in", "refresh_token", "scope");
        assertThat(json.has("access_issued_at")).isFalse();
        assertThat(json.has("refresh_expires_at")).isFalse();
    }

    @Test
    void emailCodeLoginConvertsOnlyTheEmailCodeCredentials() {
        InternalLoginService service = mock(InternalLoginService.class);
        InternalAuthController controller = new InternalAuthController(service);
        when(service.login(any())).thenReturn(tokens());

        controller.login(new InternalLoginHttpRequest(
                "admin", "email-code", null, null, "Alice@Example.test", "012345", null),
                request("2001:db8::1"), new MockHttpServletResponse());

        ArgumentCaptor<InternalLoginRequest> captor = ArgumentCaptor.forClass(InternalLoginRequest.class);
        verify(service).login(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("Alice@Example.test");
        assertThat(captor.getValue().rawEmailCode()).isEqualTo("012345");
        assertThat(captor.getValue().username()).isNull();
        assertThat(captor.getValue().rawPassword()).isNull();
        assertThat(captor.getValue().requestedScopes()).isNull();
    }

    @Test
    void refreshLoginConvertsOnlyTheRefreshCredential() {
        InternalLoginService service = mock(InternalLoginService.class);
        InternalAuthController controller = new InternalAuthController(service);
        when(service.login(any())).thenReturn(tokens());
        String refreshToken = "A".repeat(43);

        controller.login(new InternalLoginHttpRequest("user", "refresh_token", null, null, null, null,
                "note:read", refreshToken), request("192.0.2.1"), new MockHttpServletResponse());

        ArgumentCaptor<InternalLoginRequest> captor = ArgumentCaptor.forClass(InternalLoginRequest.class);
        verify(service).login(captor.capture());
        assertThat(captor.getValue().rawRefreshToken()).isEqualTo(refreshToken);
        assertThat(captor.getValue().username()).isNull();
        assertThat(captor.getValue().rawPassword()).isNull();
        assertThat(captor.getValue().email()).isNull();
        assertThat(captor.getValue().rawEmailCode()).isNull();
    }

    @Test
    void invalidOrNullDtoReturnsBadRequestWithoutCallingService() {
        InternalLoginService service = mock(InternalLoginService.class);
        InternalAuthController controller = new InternalAuthController(service);

        for (InternalLoginHttpRequest dto : new InternalLoginHttpRequest[]{
                null, new InternalLoginHttpRequest("user", null, "alice", "secret", null, null, null),
                new InternalLoginHttpRequest("user", "password", "alice", "secret", null, null, "note:read note:read")}) {
            MockHttpServletResponse servletResponse = new MockHttpServletResponse();

            Result<InternalTokenHttpResponse> result = controller.login(dto, request("192.0.2.1"), servletResponse);

            assertThat(servletResponse.getStatus()).isEqualTo(400);
            assertThat(result.getCode()).isEqualTo(Result.FAIL);
            assertThat(result.getMsg()).isEqualTo(InternalAuthController.INVALID_REQUEST_MESSAGE);
        }
        verify(service, never()).login(any());
    }

    @Test
    void malformedHttpBodyReturnsChineseBadRequestWithoutCallingService() throws Exception {
        InternalLoginService service = mock(InternalLoginService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new InternalAuthController(service)).build();

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(Result.FAIL))
                .andExpect(jsonPath("$.msg").value(InternalAuthController.INVALID_REQUEST_MESSAGE));

        verify(service, never()).login(any());
    }

    @Test
    void unsupportedClientAndGrantReturnIdentifiableAuthenticationFailures() {
        InternalLoginService service = mock(InternalLoginService.class);
        InternalAuthController controller = new InternalAuthController(service);

        assertThatThrownBy(() -> controller.login(
                new InternalLoginHttpRequest("core_agent", "password", "alice", "secret", null, null, null),
                request("192.0.2.1"), new MockHttpServletResponse()))
                .isInstanceOf(InternalAccountAuthenticationRejectedException.class)
                .hasMessage("不支持当前登录客户端");
        assertThatThrownBy(() -> controller.login(
                new InternalLoginHttpRequest("user", "authorization_code", "alice", "secret", null, null, null),
                request("192.0.2.1"), new MockHttpServletResponse()))
                .isInstanceOf(InternalAccountAuthenticationRejectedException.class)
                .hasMessage("不支持当前登录方式");
        verify(service, never()).login(any());
    }

    @Test
    void serviceAndTokenMappingExceptionsPropagate() {
        InternalLoginService authenticationFailure = mock(InternalLoginService.class);
        InternalAuthController authenticationController = new InternalAuthController(authenticationFailure);
        InternalAccountAuthenticationRejectedException rejected = new InternalAccountAuthenticationRejectedException();
        when(authenticationFailure.login(any())).thenThrow(rejected);
        assertThatThrownBy(() -> authenticationController.login(passwordRequest(), request("192.0.2.1"),
                new MockHttpServletResponse())).isSameAs(rejected);

        InternalLoginService refreshFailure = mock(InternalLoginService.class);
        InternalAuthController refreshController = new InternalAuthController(refreshFailure);
        InternalRefreshTokenRejectedException refreshRejected = new InternalRefreshTokenRejectedException();
        when(refreshFailure.login(any())).thenThrow(refreshRejected);
        assertThatThrownBy(() -> refreshController.login(new InternalLoginHttpRequest("user", "refresh_token", null,
                null, null, null, null, "A".repeat(43)), request("192.0.2.1"), new MockHttpServletResponse()))
                .isSameAs(refreshRejected);

        InternalLoginService runtimeFailure = mock(InternalLoginService.class);
        InternalAuthController runtimeController = new InternalAuthController(runtimeFailure);
        IllegalStateException failure = new IllegalStateException("issuer unavailable");
        when(runtimeFailure.login(any())).thenThrow(failure);
        assertThatThrownBy(() -> runtimeController.login(passwordRequest(), request("192.0.2.1"),
                new MockHttpServletResponse())).isSameAs(failure);

        InternalLoginService malformedTokens = mock(InternalLoginService.class);
        InternalAuthController malformedController = new InternalAuthController(malformedTokens);
        Instant issuedAt = Instant.parse("2026-08-11T00:00:00Z");
        when(malformedTokens.login(any())).thenReturn(new InternalIssuedTokens(
                "access", "refresh", "Bearer", issuedAt, issuedAt.plusMillis(999), issuedAt.plusSeconds(1), List.of()));
        assertThatThrownBy(() -> malformedController.login(passwordRequest(), request("192.0.2.1"),
                new MockHttpServletResponse())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registersController() {
        runner.withUserConfiguration(ServiceConfiguration.class)
                .run(context -> assertThat(context.getBeansOfType(InternalAuthController.class)).hasSize(1));
    }

    @Test
    void productionControllerDoesNotReferenceForwardedHeadersLogsOrLegacyFlows() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/jacolp/system/web/controller/authorization/"
                + "InternalAuthController.java"));

        assertThat(source).doesNotContain("getHeader", "X-Forwarded-For", "Forwarded", "Outbox",
                "EmailSendEventPublisher", "EmailSenderService", "TokenSessionService", "activation", "email-change",
                "Logger", "log.");
    }

    private static InternalLoginHttpRequest passwordRequest() {
        return new InternalLoginHttpRequest("user", "password", "alice", "secret", null, null, "note:read");
    }

    private static MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private static InternalIssuedTokens tokens() {
        Instant issuedAt = Instant.parse("2026-08-11T00:00:00Z");
        return new InternalIssuedTokens(
                "access.raw.token", "refresh.raw.token", "Bearer", issuedAt, issuedAt.plusSeconds(10_800),
                issuedAt.plusSeconds(259_200), List.of("note:read"));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(InternalAuthController.class)
    static class ControllerOnlyConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class ServiceConfiguration {
        @Bean
        InternalLoginService loginService() {
            return mock(InternalLoginService.class);
        }
    }
}
