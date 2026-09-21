package com.jacolp.common.security.oauth2.authorization;

import com.jacolp.common.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/** JSON-only entry point for the Phase 5 business resource-server chain. */
public final class CoreNodeJsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authenticationException) throws IOException {
        CoreNodeJsonSecurityErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, Result.error("认证失败"));
    }
}
