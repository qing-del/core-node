package com.jacolp.common.security.oauth2.authorization;

import com.jacolp.common.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/** JSON-only denied handler for the Phase 5 business resource-server chain. */
public final class CoreNodeJsonAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        CoreNodeJsonSecurityErrorWriter.write(response, HttpServletResponse.SC_FORBIDDEN, Result.error("无权访问"));
    }
}
