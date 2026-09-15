package com.jacolp.system.web.controller.authorization;

import com.jacolp.common.core.result.Result;
import com.jacolp.system.application.authorization.CoreAgentLogoutService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the authenticated CORE AGENT bearer-session logout endpoint. */
@RestController
public final class CoreAgentLogoutController {

    private final CoreAgentLogoutService logoutService;

    public CoreAgentLogoutController(CoreAgentLogoutService logoutService) {
        this.logoutService = logoutService;
    }

    @PostMapping("/oauth/logout")
    public Result<Void> logout() {
        logoutService.logout();
        return Result.success();
    }
}
