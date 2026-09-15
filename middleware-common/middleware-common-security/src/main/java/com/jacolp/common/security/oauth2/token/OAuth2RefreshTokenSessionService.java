package com.jacolp.common.security.oauth2.token;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Creates and verifies refresh session state without retaining raw credentials or authorization profile data. */
public final class OAuth2RefreshTokenSessionService {
    private final Clock clock;
    private final SecureOAuth2TokenGenerator tokenGenerator;
    private final OpaqueTokenProtector tokenProtector;
    private final OAuth2TokenStateStore stateStore;

    public OAuth2RefreshTokenSessionService(Clock clock, SecureOAuth2TokenGenerator tokenGenerator,
                                            OpaqueTokenProtector tokenProtector, OAuth2TokenStateStore stateStore) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator must not be null");
        this.tokenProtector = Objects.requireNonNull(tokenProtector, "tokenProtector must not be null");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore must not be null");
    }

    public IssuedRefreshToken issue(RefreshTokenIssueRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Instant issuedAt = clock.instant();
        Instant refreshExpiresAt = issuedAt.plus(request.refreshTtl());
        if (request.accessToken().expiresAt().isAfter(refreshExpiresAt)) {
            throw new IllegalArgumentException("access expiry must not exceed refresh expiry");
        }

        String rawToken = tokenGenerator.newOpaqueToken();
        OpaqueTokenProtection protection = tokenProtector.protect(rawToken);
        RefreshTokenState refreshState = new RefreshTokenState(protection.fingerprint(), protection.verifierHash(),
                request.userId(), request.clientId(), request.grantedScopes(), issuedAt, refreshExpiresAt);
        OAuth2SessionState sessionState = new OAuth2SessionState(request.userId(), request.clientId(),
                request.accessToken().jti(), request.accessToken().expiresAt(), protection.fingerprint(), refreshExpiresAt);
        stateStore.replaceCurrentSession(refreshState, sessionState);
        return new IssuedRefreshToken(rawToken, issuedAt, refreshExpiresAt);
    }

    public Optional<VerifiedRefreshToken> verify(String rawToken) {
        final String fingerprint;
        try {
            fingerprint = tokenProtector.fingerprint(rawToken);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        Optional<RefreshTokenState> state = stateStore.findRefreshByFingerprint(fingerprint);
        if (state.isEmpty()) return Optional.empty();
        RefreshTokenState refreshState = state.get();
        if (!fingerprint.equals(refreshState.fingerprint())) return Optional.empty();
        if (!refreshState.expiresAt().isAfter(clock.instant())) return Optional.empty();
        if (!tokenProtector.matches(rawToken, refreshState.verifierHash())) return Optional.empty();

        Optional<OAuth2SessionState> session = stateStore.findSession(refreshState.clientId(), refreshState.userId());
        if (session.isEmpty() || !matchesCurrentSession(refreshState, session.get())) return Optional.empty();
        return Optional.of(new VerifiedRefreshToken(refreshState.fingerprint(), refreshState.userId(), refreshState.clientId(),
                refreshState.grantedScopes(), refreshState.expiresAt()));
    }

    /**
     * Rotates a refresh session only after re-verifying the caller's raw refresh token and current session pointer.
     * The verified identity and client are internal to this method and cannot be supplied by a caller.
     *
     * <p>{@code nextGrantedScopes} is the immutable result of the caller's current authorization-policy evaluation;
     * this storage-layer service validates and persists it but does not decide scope entitlement or wildcard
     * narrowing. An empty collection remains valid because {@link RefreshTokenState} permits it.</p>
     */
    public Optional<IssuedRefreshToken> rotate(String currentRawToken, AccessTokenSessionReference nextAccessToken,
                                               List<String> nextGrantedScopes, Duration nextRefreshTtl) {
        nextAccessToken = Objects.requireNonNull(nextAccessToken, "nextAccessToken must not be null");
        nextGrantedScopes = List.copyOf(Objects.requireNonNull(nextGrantedScopes, "nextGrantedScopes must not be null"));
        nextRefreshTtl = Objects.requireNonNull(nextRefreshTtl, "nextRefreshTtl must not be null");
        if (nextRefreshTtl.isZero() || nextRefreshTtl.isNegative()) {
            throw new IllegalArgumentException("nextRefreshTtl must be positive");
        }

        Optional<VerifiedRefreshToken> verified = verify(currentRawToken);
        if (verified.isEmpty()) return Optional.empty();
        Instant issuedAt = clock.instant();
        Instant nextRefreshExpiresAt = issuedAt.plus(nextRefreshTtl);
        if (nextAccessToken.expiresAt().isAfter(nextRefreshExpiresAt)) {
            throw new IllegalArgumentException("access expiry must not exceed refresh expiry");
        }

        String nextRawToken = tokenGenerator.newOpaqueToken();
        OpaqueTokenProtection protection = tokenProtector.protect(nextRawToken);
        VerifiedRefreshToken current = verified.get();
        RefreshTokenState nextRefreshState = new RefreshTokenState(protection.fingerprint(), protection.verifierHash(),
                current.userId(), current.clientId(), nextGrantedScopes, issuedAt, nextRefreshExpiresAt);
        OAuth2SessionState nextSessionState = new OAuth2SessionState(current.userId(), current.clientId(),
                nextAccessToken.jti(), nextAccessToken.expiresAt(), protection.fingerprint(), nextRefreshExpiresAt);
        if (!stateStore.rotate(current.fingerprint(), nextRefreshState, nextSessionState)) return Optional.empty();
        return Optional.of(new IssuedRefreshToken(nextRawToken, issuedAt, nextRefreshExpiresAt));
    }

    private static boolean matchesCurrentSession(RefreshTokenState refreshState, OAuth2SessionState sessionState) {
        return refreshState.userId() == sessionState.userId()
                && refreshState.clientId().equals(sessionState.clientId())
                && refreshState.fingerprint().equals(sessionState.currentRefreshFingerprint())
                && refreshState.expiresAt().equals(sessionState.refreshExpiresAt());
    }
}
