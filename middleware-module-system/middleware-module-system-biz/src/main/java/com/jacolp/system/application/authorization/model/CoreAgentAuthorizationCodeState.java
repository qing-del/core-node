package com.jacolp.system.application.authorization.model;

import com.jacolp.system.application.authorization.ClientAllowedIpPolicy;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable, application-only state for one unexchanged CORE AGENT authorization code.
 *
 * <p>The original socket literal is retained exactly as supplied by {@code getRemoteAddr()}, after
 * literal-only validation. It is not normalized, so Phase 4 can warn on a changed remote literal
 * without trusting forwarded headers. The OAuth state cap is a deliberately broad 8 KiB resource
 * limit, not an OAuth protocol limit.</p>
 */
public record CoreAgentAuthorizationCodeState(
        String rawCode,
        String clientId,
        String redirectUri,
        List<String> scopes,
        String codeChallenge,
        String codeChallengeMethod,
        String originalSocketAddress,
        String oauthState,
        Instant issuedAt,
        Instant expiresAt,
        CoreAgentAuthorizationAccountSnapshot accountSnapshot) {

    public static final String CORE_AGENT_CLIENT_ID = "core_agent";
    public static final String PKCE_S256 = "S256";
    public static final Duration TIME_TO_LIVE = Duration.ofMinutes(10);
    private static final int OAUTH_STATE_MAXIMUM_LENGTH = 8192;
    private static final ClientAllowedIpPolicy ANY_LITERAL_SOCKET_IP =
            ClientAllowedIpPolicy.parse("0.0.0.0/0,::/0");

    public CoreAgentAuthorizationCodeState {
        require256BitBase64Url(rawCode, "rawCode");
        if (!CORE_AGENT_CLIENT_ID.equals(clientId)) {
            throw invalid("clientId must be core_agent");
        }
        redirectUri = requireSafeRedirectUri(redirectUri);
        scopes = normalizedScopes(scopes);
        require256BitBase64Url(codeChallenge, "codeChallenge");
        if (!PKCE_S256.equals(codeChallengeMethod)) {
            throw invalid("codeChallengeMethod must be S256");
        }
        originalSocketAddress = requireOriginalSocketLiteral(originalSocketAddress);
        requireSafeState(oauthState);
        if (issuedAt == null || expiresAt == null || !issuedAt.plus(TIME_TO_LIVE).equals(expiresAt)) {
            throw invalid("issuedAt and expiresAt must be exactly ten minutes apart");
        }
        if (accountSnapshot == null) {
            throw invalid("accountSnapshot is required");
        }
    }

    @Override
    public String toString() {
        return "CoreAgentAuthorizationCodeState[rawCode=<redacted>, clientId=" + clientId
                + ", redirectUri=" + redirectUri + ", scopes=" + scopes + ", codeChallenge=<redacted>"
                + ", codeChallengeMethod=" + codeChallengeMethod + ", originalSocketAddress=<redacted>"
                + ", oauthState=<redacted>, issuedAt=" + issuedAt + ", expiresAt=" + expiresAt
                + ", accountSnapshot=" + accountSnapshot + ']';
    }

    static void require256BitBase64Url(String value, String name) {
        if (value == null || value.length() != 43) {
            throw invalid(name + " must be a 256-bit Base64URL value");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (decoded.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value)) {
                throw invalid(name + " must be a canonical 256-bit Base64URL value");
            }
        } catch (IllegalArgumentException exception) {
            throw invalid(name + " must be a canonical 256-bit Base64URL value");
        }
    }

    static String requireSafeRedirectUri(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank() || !redirectUri.equals(redirectUri.trim())) {
            throw invalid("redirectUri is required");
        }
        try {
            URI uri = new URI(redirectUri);
            String scheme = uri.getScheme();
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw invalid("redirectUri must be an absolute HTTP(S) URI without user info or fragment");
            }
            return redirectUri;
        } catch (URISyntaxException exception) {
            throw invalid("redirectUri must be a valid URI");
        }
    }

    static List<String> normalizedScopes(Collection<String> candidateScopes) {
        if (candidateScopes == null || candidateScopes.isEmpty()) {
            throw invalid("scopes cannot be empty");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String scope : candidateScopes) {
            if (scope == null || scope.isEmpty() || !scope.equals(scope.trim())) {
                throw invalid("scopes contain an invalid value");
            }
            try {
                String canonical = PermissionScopePattern.parse(scope).asScope();
                if (!scope.equals(canonical) || !normalized.add(canonical)) {
                    throw invalid("scopes contain a duplicate or non-canonical value");
                }
            } catch (IllegalArgumentException exception) {
                throw invalid("scopes contain an invalid permission pattern");
            }
        }
        List<String> sorted = new ArrayList<>(normalized);
        sorted.sort(String::compareTo);
        return List.copyOf(sorted);
    }

    static String requireOriginalSocketLiteral(String socketAddress) {
        if (socketAddress == null || socketAddress.isBlank() || !socketAddress.equals(socketAddress.trim())) {
            throw invalid("originalSocketAddress must be a socket IP literal");
        }
        try {
            if (!ANY_LITERAL_SOCKET_IP.allows(socketAddress)) {
                throw invalid("originalSocketAddress must be a socket IP literal");
            }
        } catch (IllegalArgumentException exception) {
            throw invalid("originalSocketAddress must be a socket IP literal");
        }
        return socketAddress;
    }

    static void requireSafeState(String state) {
        if (state == null || state.isBlank() || state.length() > OAUTH_STATE_MAXIMUM_LENGTH) {
            throw invalid("oauthState is required and exceeds the supported size");
        }
        for (int index = 0; index < state.length(); index++) {
            if (Character.isISOControl(state.charAt(index))) {
                throw invalid("oauthState cannot contain control characters");
            }
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid CORE AGENT authorization code state: " + message);
    }
}
