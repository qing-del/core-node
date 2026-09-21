package com.jacolp.system.web.controller.authorization;

import com.jacolp.common.core.result.Result;
import com.jacolp.system.application.authorization.InternalLogoutService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the internal USER and ADMIN current-session logout endpoint. */
@RestController
@RequestMapping("/auth")
public class InternalLogoutController {
    private final InternalLogoutService logoutService;

    public InternalLogoutController(InternalLogoutService logoutService) {
        this.logoutService = logoutService;
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        logoutService.logout();
        return Result.success();
    }
}
