package com.jacolp.system.web.controller.authorization;

import com.jacolp.common.core.result.Result;
import com.jacolp.system.application.authorization.model.EmailLoginCodeIssueRequest;
import com.jacolp.system.application.dto.authorization.EmailLoginCodeHttpRequest;
import com.jacolp.system.application.authorization.EmailLoginCodeIssuanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Issues an email login code response for internal clients. */
@RestController
@RequestMapping("/oauth")
public class OAuthEmailCodeController {

    static final String INVALID_REQUEST_MESSAGE = "Invalid email-code request";
    static final String SUCCESS_MESSAGE = "If the account is eligible, the login code has been sent";

    private final EmailLoginCodeIssuanceService issuanceService;

    public OAuthEmailCodeController(EmailLoginCodeIssuanceService issuanceService) {
        this.issuanceService = issuanceService;
    }

    @PostMapping("/email-code")
    public Result<Void> issueEmailCode(
            @RequestBody EmailLoginCodeHttpRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        EmailLoginCodeIssueRequest issueRequest;
        try {
            if (request == null) {
                throw new IllegalArgumentException("Email-code request is required");
            }
            issueRequest = request.toDomain(servletRequest.getRemoteAddr());
        } catch (IllegalArgumentException exception) {
            servletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return Result.error(INVALID_REQUEST_MESSAGE);
        }

        issuanceService.issue(issueRequest);
        Result<Void> result = Result.success();
        result.setMsg(SUCCESS_MESSAGE);
        return result;
    }
}
