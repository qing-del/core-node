package com.jacolp.system.application.authorization;

import com.jacolp.common.core.exception.AuthenticationException;

/** Uniform non-enumerating rejection after a CORE AGENT authorization-code exchange. */
public final class CoreAgentAuthorizationCodeTokenRejectedException extends AuthenticationException {

    public static final String MESSAGE = "CORE AGENT authorization code token issuance rejected";

    public CoreAgentAuthorizationCodeTokenRejectedException() {
        super(MESSAGE);
    }
}
