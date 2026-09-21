package com.jacolp.system.application.authorization.model;

import java.util.Objects;

/** Opaque pending handle and its complete Redis state, returned only inside the authorization flow. */
public record CoreAgentPreparedPendingAuthorization(
        IssuedCoreAgentAuthorizationPendingHandle handle,
        CoreAgentPendingAuthorizationState state) {

    public CoreAgentPreparedPendingAuthorization {
        handle = Objects.requireNonNull(handle, "handle");
        state = Objects.requireNonNull(state, "state");
        if (!handle.expiresAt().equals(state.expiresAt())) {
            throw new IllegalArgumentException("CORE AGENT pending handle and state expiry must match");
        }
    }

    @Override
    public String toString() {
        return "CoreAgentPreparedPendingAuthorization[handle=<redacted>, state=<redacted>]";
    }
}
