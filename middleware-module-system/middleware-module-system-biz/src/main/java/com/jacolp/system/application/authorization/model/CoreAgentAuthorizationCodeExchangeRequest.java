package com.jacolp.system.application.authorization.model;

import com.jacolp.system.application.authorization.ClientAllowedIpPolicy;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.regex.Pattern;

/** Strict application request for one public CORE AGENT authorization-code exchange. */
public record CoreAgentAuthorizationCodeExchangeRequest(
        String rawCode,
        String clientId,
        String redirectUri,
        String codeVerifier,
        String socketRemoteAddress) {

    private static final Pattern RFC7636_VERIFIER = Pattern.compile("[A-Za-z0-9\\-._~]{43,128}");
    private static final ClientAllowedIpPolicy ANY_LITERAL_SOCKET_IP =
            ClientAllowedIpPolicy.parse("0.0.0.0/0,::/0");

    public CoreAgentAuthorizationCodeExchangeRequest {
        new IssuedCoreAgentAuthorizationCode(rawCode, Instant.EPOCH);
        requireText(clientId, "clientId");
        redirectUri = requireSafeRedirectUri(redirectUri);
        if (codeVerifier == null || !RFC7636_VERIFIER.matcher(codeVerifier).matches()) {
            throw invalid("codeVerifier must be an RFC 7636 unreserved value between 43 and 128 characters");
        }
        if (socketRemoteAddress == null || socketRemoteAddress.isBlank()
                || !socketRemoteAddress.equals(socketRemoteAddress.trim())) {
            throw invalid("socketRemoteAddress must be an IP literal");
        }
        try {
            if (!ANY_LITERAL_SOCKET_IP.allows(socketRemoteAddress)) {
                throw invalid("socketRemoteAddress must be an IP literal");
            }
        } catch (IllegalArgumentException exception) {
            throw invalid("socketRemoteAddress must be an IP literal");
        }
    }

    @Override
    public String toString() {
        return "CoreAgentAuthorizationCodeExchangeRequest[rawCode=<redacted>, clientId=<redacted>, redirectUri=<redacted>"
                + ", codeVerifier=<redacted>, socketRemoteAddress=<redacted>]";
    }

    private static String requireSafeRedirectUri(String redirectUri) {
        requireText(redirectUri, "redirectUri");
        if (!redirectUri.equals(redirectUri.trim())) {
            throw invalid("redirectUri cannot contain surrounding whitespace");
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

    private static void requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw invalid(name + " is required");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid CORE AGENT authorization-code exchange request: " + message);
    }
}
