package com.jacolp.system.application.authorization;

import com.jacolp.common.core.exception.AuthenticationException;

/** Stable, non-sensitive rejection when no eligible internal access token is present. */
public final class InternalLogoutRejectedException extends AuthenticationException {
    public InternalLogoutRejectedException() {
        super("Internal logout rejected");
    }
}
