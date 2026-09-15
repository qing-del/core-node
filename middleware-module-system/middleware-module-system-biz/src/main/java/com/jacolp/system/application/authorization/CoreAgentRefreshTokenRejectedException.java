package com.jacolp.system.application.authorization;

import com.jacolp.common.core.exception.AuthenticationException;

/** Uniform non-enumerating rejection for a CORE AGENT refresh-token grant. */
public final class CoreAgentRefreshTokenRejectedException extends AuthenticationException {

    public static final String MESSAGE = "CORE AGENT refresh token rejected";

    public CoreAgentRefreshTokenRejectedException() {
        super(MESSAGE);
    }
}
