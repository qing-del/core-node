package com.jacolp.common.security.context;

import java.util.Optional;

/** Reads the current RS256 access-token reference without exposing its bearer value. */
public interface CurrentAccessTokenAccessor {
    Optional<CurrentAccessTokenReference> currentAccessToken();
}
