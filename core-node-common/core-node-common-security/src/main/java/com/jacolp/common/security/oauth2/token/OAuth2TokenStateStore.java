package com.jacolp.common.security.oauth2.token;

import java.util.Optional;

public interface OAuth2TokenStateStore {
    /**
     * Unconditionally records the current session for a client-user pair with its newly issued refresh state.
     *
     * <p>A later concurrent issuance wins by replacing the session pointer. Previous refresh hashes can remain
     * until their natural TTL expires; refresh redemption must verify this session's current fingerprint before
     * accepting one of them.</p>
     */
    void replaceCurrentSession(RefreshTokenState refreshState, OAuth2SessionState sessionState);

    /**
     * Atomically replaces the current refresh session only when it still references {@code expectedOldFingerprint}.
     *
     * @return {@code true} when the compare-and-rotate succeeded, or {@code false} when the old refresh/session
     * pointer no longer matches
     */
    boolean rotate(String expectedOldFingerprint, RefreshTokenState nextRefreshState, OAuth2SessionState nextSessionState);

    Optional<RefreshTokenState> findRefreshByFingerprint(String fingerprint);
    Optional<OAuth2SessionState> findSession(String clientId, long userId);
    void deleteRefresh(String fingerprint);
    void deleteSession(String clientId, long userId);
}
