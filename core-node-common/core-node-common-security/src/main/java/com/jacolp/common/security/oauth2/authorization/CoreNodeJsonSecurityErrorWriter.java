package com.jacolp.common.security.oauth2.authorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.common.core.json.JacksonObjectMapper;
import com.jacolp.common.core.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

final class CoreNodeJsonSecurityErrorWriter {

    private static final ObjectMapper OBJECT_MAPPER = new JacksonObjectMapper();

    private CoreNodeJsonSecurityErrorWriter() {
    }

    static void write(HttpServletResponse response, int status, Result<?> body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        OBJECT_MAPPER.writeValue(response.getWriter(), body);
    }
}
