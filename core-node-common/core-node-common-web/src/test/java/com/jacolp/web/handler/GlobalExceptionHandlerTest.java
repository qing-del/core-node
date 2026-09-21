package com.jacolp.web.handler;

import com.jacolp.common.core.exception.AuthenticationException;
import com.jacolp.common.core.exception.BaseException;
import com.jacolp.common.core.exception.PermissionDeniedException;
import com.jacolp.common.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ErrorController(new OwnershipService()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void permissionDeniedMapsToForbiddenWithTheEstablishedResultBody() throws Exception {
        mvc.perform(get("/test/scope").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("scope denied"));
    }

    @Test
    void ownershipPermissionDeniedAlsoMapsToForbiddenForLegacyAndRsCallers() throws Exception {
        mvc.perform(get("/test/ownership").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("ownership denied"));
    }

    @Test
    void otherBusinessExceptionsKeepTheirExistingStatusAndAuthenticationStaysUnauthorized() throws Exception {
        mvc.perform(get("/test/business")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.msg").value("business failure"));
        mvc.perform(get("/test/authentication")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.msg").value("authentication failure"));
        mvc.perform(get("/test/password-authentication")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("请检查账号和密码是否正确"));
    }

    @Test
    void authenticationExceptionSeparatesChineseResponseMessageFromEnglishLogMessage() {
        AuthenticationException exception = new AuthenticationException("请检查账号和密码是否正确",
                "Internal login password does not match");

        assertThat(exception.getMessage()).isEqualTo("请检查账号和密码是否正确");
        assertThat(exception.getLogMessage()).isEqualTo("Internal login password does not match");
    }

    @RestController
    static class ErrorController {
        private final OwnershipService ownershipService;

        ErrorController(OwnershipService ownershipService) {
            this.ownershipService = ownershipService;
        }

        @GetMapping("/test/scope")
        void scope() {
            throw new PermissionDeniedException("scope denied");
        }

        @GetMapping("/test/ownership")
        void ownership() {
            ownershipService.requireOwner();
        }

        @GetMapping("/test/business")
        void business() {
            throw new BaseException("business failure");
        }

        @GetMapping("/test/authentication")
        void authentication() {
            throw new AuthenticationException("authentication failure");
        }

        @GetMapping("/test/password-authentication")
        void passwordAuthentication() {
            throw new AuthenticationException("请检查账号和密码是否正确", "Internal login password does not match");
        }
    }

    static class OwnershipService {
        void requireOwner() {
            throw new PermissionDeniedException("ownership denied");
        }
    }
}
