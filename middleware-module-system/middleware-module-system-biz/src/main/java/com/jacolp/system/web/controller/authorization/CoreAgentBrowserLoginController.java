package com.jacolp.system.web.controller.authorization;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Renders the browser login page used only by the CORE AGENT authorization flow.
 *
 * <p>The matching POST is intentionally not mapped here. A later Spring Security form-login
 * configuration owns credential processing so this controller never handles credentials.</p>
 */
@Controller
public final class CoreAgentBrowserLoginController {

    static final String GENERIC_LOGIN_ERROR_MESSAGE = "登录失败，请检查账号或密码后重试。";

    @GetMapping("/oauth/login")
    public String login(@RequestParam(value = "error", required = false) String error, Model model) {
        if (error != null) {
            model.addAttribute("oauthLoginError", GENERIC_LOGIN_ERROR_MESSAGE);
        }
        return "oauth/login";
    }
}
