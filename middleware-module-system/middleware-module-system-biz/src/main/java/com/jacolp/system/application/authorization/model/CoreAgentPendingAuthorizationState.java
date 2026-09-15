package com.jacolp.system.application.authorization.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Complete Redis-resident state for a browser authorization request awaiting consent or code
 * conversion.
 *
 * <p>This deliberately contains neither a raw authorization code nor account security fields.
 * The session identifier binds the Redis transaction to the browser session that retained its
 * opaque pending handle. A nullable {@code requestedScopes} preserves OAuth's omitted-scope
 * semantics; a supplied list is strict, canonical, immutable, and non-empty.</p>
 */
public record CoreAgentPendingAuthorizationState(
        String clientId,
        String redirectUri,
        List<String> requestedScopes,
        String codeChallenge,
        String codeChallengeMethod,
        String oauthState,
        String originalSocketAddress,
        long authenticatedUserId,
        String sessionId,
        Instant issuedAt,
        Instant expiresAt) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final Duration TIME_TO_LIVE = Duration.ofMinutes(10);
    private static final int SESSION_ID_MAXIMUM_LENGTH = 512;

    public CoreAgentPendingAuthorizationState {
        if (!CoreAgentAuthorizationCodeState.CORE_AGENT_CLIENT_ID.equals(clientId)) {
            throw invalid();
        }
        redirectUri = CoreAgentAuthorizationCodeState.requireSafeRedirectUri(redirectUri);
        requestedScopes = requestedScopes == null ? null : CoreAgentAuthorizationCodeState.normalizedScopes(requestedScopes);
        CoreAgentAuthorizationCodeState.require256BitBase64Url(codeChallenge, "codeChallenge");
        if (!CoreAgentAuthorizationCodeState.PKCE_S256.equals(codeChallengeMethod)) {
            throw invalid();
        }
        CoreAgentAuthorizationCodeState.requireSafeState(oauthState);
        originalSocketAddress = CoreAgentAuthorizationCodeState.requireOriginalSocketLiteral(originalSocketAddress);
        if (authenticatedUserId <= 0) {
            throw invalid();
        }
        sessionId = requireSessionId(sessionId);
        if (issuedAt == null || expiresAt == null || !issuedAt.plus(TIME_TO_LIVE).equals(expiresAt)) {
            throw invalid();
        }
    }

    /** Requires an opaque servlet session identifier without control characters. */
    public static String requireSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank() || !sessionId.equals(sessionId.trim())
                || sessionId.length() > SESSION_ID_MAXIMUM_LENGTH) {
            throw invalid();
        }
        for (int index = 0; index < sessionId.length(); index++) {
            if (Character.isISOControl(sessionId.charAt(index))) {
                throw invalid();
            }
        }
        return sessionId;
    }

    @Override
    public String toString() {
        return "CoreAgentPendingAuthorizationState[clientId=" + clientId + ", redirectUri=" + redirectUri
                + ", requestedScopes=" + requestedScopes + ", codeChallenge=<redacted>, codeChallengeMethod="
                + codeChallengeMethod + ", oauthState=<redacted>, originalSocketAddress=<redacted>"
                + ", authenticatedUserId=" + authenticatedUserId + ", sessionId=<redacted>, issuedAt="
                + issuedAt + ", expiresAt=" + expiresAt + ']';
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid CORE AGENT pending authorization state");
    }
}
