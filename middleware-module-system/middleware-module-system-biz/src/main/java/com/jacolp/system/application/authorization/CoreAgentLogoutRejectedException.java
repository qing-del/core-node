package com.jacolp.system.application.authorization;

import com.jacolp.common.core.exception.AuthenticationException;

/** Stable, non-sensitive rejection when no eligible CORE AGENT access token is present. */
public final class CoreAgentLogoutRejectedException extends AuthenticationException {

    public static final String MESSAGE = "CORE AGENT logout rejected";

    public CoreAgentLogoutRejectedException() {
        super(MESSAGE);
    }
}
