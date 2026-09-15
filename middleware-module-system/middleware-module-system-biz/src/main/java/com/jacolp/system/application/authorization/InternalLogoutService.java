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

/** Revokes only the authenticated USER or ADMIN client session using bounded CAS retries. */
@Service
public class InternalLogoutService {
    private static final int MAX_REVOCATION_ATTEMPTS = 3;

    private final CurrentAccessTokenAccessor accessTokenAccessor;
    private final OAuth2TokenStateStore tokenStateStore;
    private final OAuth2SessionRevocationStore revocationStore;

    public InternalLogoutService(CurrentAccessTokenAccessor accessTokenAccessor, OAuth2TokenStateStore tokenStateStore,
                                 OAuth2SessionRevocationStore revocationStore) {
        this.accessTokenAccessor = Objects.requireNonNull(accessTokenAccessor, "accessTokenAccessor");
        this.tokenStateStore = Objects.requireNonNull(tokenStateStore, "tokenStateStore");
        this.revocationStore = Objects.requireNonNull(revocationStore, "revocationStore");
    }

    public void logout() {
        CurrentAccessTokenReference access = accessTokenAccessor.currentAccessToken()
                .orElseThrow(InternalLogoutRejectedException::new);
        if (!"user".equals(access.clientId()) && !"admin".equals(access.clientId())) {
            throw new InternalLogoutRejectedException();
        }
        for (int attempt = 0; attempt < MAX_REVOCATION_ATTEMPTS; attempt++) {
            Optional<OAuth2SessionState> state = tokenStateStore.findSession(access.clientId(), access.userId());
            if (state == null) {
                throw new IllegalStateException("Internal logout session lookup returned null");
            }
            String expectedFingerprint = state.map(session -> fingerprint(access, session)).orElse(null);
            if (revocationStore.revoke(new OAuth2SessionRevocationRequest(access.userId(), access.clientId(), access.jti(),
                    access.expiresAt(), expectedFingerprint))) {
                return;
            }
        }
        throw new IllegalStateException("Internal logout session revocation remained stale");
    }

    private static String fingerprint(CurrentAccessTokenReference access, OAuth2SessionState session) {
        if (session.userId() != access.userId() || !access.clientId().equals(session.clientId())) {
            throw new IllegalStateException("Internal logout session identity is inconsistent");
        }
        return session.currentRefreshFingerprint();
    }
}
