package com.jacolp.system.application.authorization;

import com.jacolp.common.security.context.CurrentAccessTokenAccessor;
import com.jacolp.common.security.context.CurrentAccessTokenReference;
import com.jacolp.common.security.oauth2.token.OAuth2SessionRevocationRequest;
import com.jacolp.common.security.oauth2.token.OAuth2SessionRevocationStore;
import com.jacolp.common.security.oauth2.token.OAuth2SessionState;
import com.jacolp.common.security.oauth2.token.OAuth2TokenStateStore;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/** Revokes only the authenticated CORE AGENT client session using bounded compare-and-set retries. */
@Service
public final class CoreAgentLogoutService {

    private static final String CORE_AGENT_CLIENT_ID = CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID;
    private static final int MAX_REVOCATION_ATTEMPTS = 3;

    private final CurrentAccessTokenAccessor accessTokenAccessor;
    private final OAuth2TokenStateStore tokenStateStore;
    private final OAuth2SessionRevocationStore revocationStore;

    public CoreAgentLogoutService(CurrentAccessTokenAccessor accessTokenAccessor, OAuth2TokenStateStore tokenStateStore,
                                  OAuth2SessionRevocationStore revocationStore) {
        this.accessTokenAccessor = Objects.requireNonNull(accessTokenAccessor, "accessTokenAccessor");
        this.tokenStateStore = Objects.requireNonNull(tokenStateStore, "tokenStateStore");
        this.revocationStore = Objects.requireNonNull(revocationStore, "revocationStore");
    }

    /**
     * Revokes the current access JTI and, when present, the current refresh session fingerprint.
     * A missing refresh session still invokes revocation with a {@code null} fingerprint so the access-token
     * blacklist is written and any stale session pointer can be removed by the store.
     */
    public void logout() {
        CurrentAccessTokenReference access = accessTokenAccessor.currentAccessToken()
                .orElseThrow(CoreAgentLogoutRejectedException::new);
        if (!CORE_AGENT_CLIENT_ID.equals(access.clientId())) {
            throw rejected();
        }
        for (int attempt = 0; attempt < MAX_REVOCATION_ATTEMPTS; attempt++) {
            Optional<OAuth2SessionState> state = tokenStateStore.findSession(CORE_AGENT_CLIENT_ID, access.userId());
            if (state == null) {
                throw new IllegalStateException("CORE AGENT logout session lookup returned null");
            }
            String expectedFingerprint = state.map(session -> fingerprint(access, session)).orElse(null);
            if (revocationStore.revoke(new OAuth2SessionRevocationRequest(access.userId(), CORE_AGENT_CLIENT_ID,
                    access.jti(), access.expiresAt(), expectedFingerprint))) {
                return;
            }
        }
        throw new IllegalStateException("CORE AGENT logout session revocation remained stale");
    }

    private static String fingerprint(CurrentAccessTokenReference access, OAuth2SessionState session) {
        if (session.userId() != access.userId() || !CORE_AGENT_CLIENT_ID.equals(session.clientId())) {
            throw new IllegalStateException("CORE AGENT logout session identity is inconsistent");
        }
        return session.currentRefreshFingerprint();
    }

    private static CoreAgentLogoutRejectedException rejected() {
        return new CoreAgentLogoutRejectedException();
    }
}
