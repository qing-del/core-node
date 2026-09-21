package com.jacolp.system.application.authorization.model;

import com.jacolp.system.application.authorization.ClientAllowedIpPolicy;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Strict, application-only request for issuing one CORE AGENT authorization code. */
public record CoreAgentAuthorizationCodeIssueRequest(
        Long authenticatedUserId,
        String clientId,
        String redirectUri,
        List<String> requestedScopes,
        List<String> submittedOptionalScopes,
        String codeChallenge,
        String codeChallengeMethod,
        String socketRemoteAddress,
        String oauthState) {

    private static final int OAUTH_STATE_MAXIMUM_LENGTH = 8192;
    private static final ClientAllowedIpPolicy ANY_LITERAL_SOCKET_IP =
            ClientAllowedIpPolicy.parse("0.0.0.0/0,::/0");

    public CoreAgentAuthorizationCodeIssueRequest {
        if (authenticatedUserId == null || authenticatedUserId <= 0) {
            throw invalid("authenticatedUserId must be positive");
        }
        requireText(clientId, "clientId");
        redirectUri = requireSafeRedirectUri(redirectUri);
        requestedScopes = requestedScopes == null ? null : defensiveCopy(requestedScopes, "requestedScopes");
        submittedOptionalScopes = defensiveCopy(submittedOptionalScopes, "submittedOptionalScopes");
        CoreAgentAuthorizationCodeState.require256BitBase64Url(codeChallenge, "codeChallenge");
        if (!CoreAgentAuthorizationCodeState.PKCE_S256.equals(codeChallengeMethod)) {
            throw invalid("codeChallengeMethod must be S256");
        }
        socketRemoteAddress = requireSocketLiteral(socketRemoteAddress);
        oauthState = requireSafeState(oauthState);
    }

    @Override
    public String toString() {
        return "CoreAgentAuthorizationCodeIssueRequest[authenticatedUserId=<redacted>, clientId=<redacted>"
                + ", redirectUri=<redacted>, requestedScopes=<redacted>, submittedOptionalScopes=<redacted>"
                + ", codeChallenge=<redacted>, codeChallengeMethod=<redacted>, socketRemoteAddress=<redacted>"
                + ", oauthState=<redacted>]";
    }

    private static List<String> defensiveCopy(Collection<String> values, String name) {
        if (values == null) {
            throw invalid(name + " cannot be null");
        }
        List<String> copy = new ArrayList<>(values.size());
        for (String value : values) {
            if (value == null) {
                throw invalid(name + " cannot contain null");
            }
            copy.add(value);
        }
        return List.copyOf(copy);
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

    private static String requireSocketLiteral(String socketRemoteAddress) {
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
        return socketRemoteAddress;
    }

    private static String requireSafeState(String state) {
        if (state == null || state.isBlank() || state.length() > OAUTH_STATE_MAXIMUM_LENGTH) {
            throw invalid("oauthState is required and exceeds the supported size");
        }
        for (int index = 0; index < state.length(); index++) {
            if (Character.isISOControl(state.charAt(index))) {
                throw invalid("oauthState cannot contain control characters");
            }
        }
        return state;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw invalid(name + " is required");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid CORE AGENT authorization-code issue request: " + message);
    }
}
