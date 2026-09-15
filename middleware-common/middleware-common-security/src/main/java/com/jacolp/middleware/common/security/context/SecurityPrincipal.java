package com.jacolp.middleware.common.security.context;

import java.util.Objects;

/** Typed principal used by the compatibility SecurityContext bridge. */
public record SecurityPrincipal(Long id, SecurityIdentity identity) {

    public SecurityPrincipal {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(identity, "identity must not be null");
    }
}
