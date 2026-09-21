package com.jacolp.system.web.controller.authorization;

import com.jacolp.common.core.result.Result;
import com.jacolp.system.application.authorization.InternalAccountAuthenticationRejectedException;
import com.jacolp.system.application.authorization.model.InternalIssuedTokens;
import com.jacolp.system.application.authorization.model.InternalLoginRequest;
import com.jacolp.system.application.dto.authorization.InternalLoginHttpRequest;
import com.jacolp.system.application.dto.authorization.InternalTokenHttpResponse;
import com.jacolp.system.application.authorization.InternalLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.Set;

/** Handles USER/ADMIN password, email-code, and refresh-token grants on the one internal login endpoint. */
@RestController
@RequestMapping("/auth")
public class InternalAuthController {

    static final String INVALID_REQUEST_MESSAGE = "登录请求参数无效";
    private static final Set<String> SUPPORTED_CLIENT_IDS = Set.of("user", "admin");
    private static final Set<String> SUPPORTED_GRANT_TYPES = Set.of("password", "email-code", "refresh_token");

    private final InternalLoginService loginService;

    public InternalAuthController(InternalLoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    public Result<InternalTokenHttpResponse> login(
            @RequestBody InternalLoginHttpRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        InternalLoginRequest loginRequest;
        try {
            if (request == null) {
                throw new IllegalArgumentException("Internal login request is required");
            }
            rejectUnsupportedClientOrGrant(request);
            loginRequest = request.toDomain(servletRequest.getRemoteAddr());
        } catch (IllegalArgumentException exception) {
            servletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return Result.error(INVALID_REQUEST_MESSAGE);
        }

        InternalIssuedTokens tokens = loginService.login(loginRequest);
        return Result.success(InternalTokenHttpResponse.from(tokens));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public Result<InternalTokenHttpResponse> invalidRequest(HttpServletResponse servletResponse) {
        servletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return Result.error(INVALID_REQUEST_MESSAGE);
    }

    private static void rejectUnsupportedClientOrGrant(InternalLoginHttpRequest request) {
        if (hasText(request.clientId()) && !SUPPORTED_CLIENT_IDS.contains(request.clientId())) {
            throw new InternalAccountAuthenticationRejectedException(
                    InternalAccountAuthenticationRejectedException.Reason.UNSUPPORTED_CLIENT);
        }
        if (hasText(request.grantType()) && !SUPPORTED_GRANT_TYPES.contains(request.grantType())) {
            throw new InternalAccountAuthenticationRejectedException(
                    InternalAccountAuthenticationRejectedException.Reason.UNSUPPORTED_GRANT_TYPE);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
