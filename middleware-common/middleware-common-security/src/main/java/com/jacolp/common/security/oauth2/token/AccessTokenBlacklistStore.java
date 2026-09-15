package com.jacolp.common.security.oauth2.token;

import java.time.Instant;

/** Stores revocation state by access-token identifier only. */
public interface AccessTokenBlacklistStore {

    void blacklist(String jti, Instant expiresAt);

    boolean isBlacklisted(String jti);
}
