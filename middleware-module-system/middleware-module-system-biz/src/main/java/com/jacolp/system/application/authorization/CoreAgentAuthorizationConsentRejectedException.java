package com.jacolp.system.application.authorization;

import com.jacolp.common.core.exception.AuthenticationException;

/** Uniform non-enumerating rejection for an invalid CORE AGENT browser consent submission. */
public final class CoreAgentAuthorizationConsentRejectedException extends AuthenticationException {

    public static final String MESSAGE = "CORE AGENT authorization consent rejected";

    public CoreAgentAuthorizationConsentRejectedException() {
        super(MESSAGE);
    }
}
