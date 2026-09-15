package com.jacolp.system.application.authorization;

import com.jacolp.common.core.exception.AuthenticationException;

/** Uniform non-enumerating rejection while issuing a CORE AGENT authorization code. */
public final class CoreAgentAuthorizationCodeIssueRejectedException extends AuthenticationException {

    public static final String MESSAGE = "CORE AGENT authorization code issuance rejected";

    public CoreAgentAuthorizationCodeIssueRejectedException() {
        super(MESSAGE);
    }
}
