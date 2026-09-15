package com.jacolp.common.security.context;

import java.util.Optional;

/** Reads the authenticated identity normalized from the current Spring Security context. */
public interface CurrentPrincipalAccessor {
    Optional<CurrentPrincipal> currentPrincipal();
}
