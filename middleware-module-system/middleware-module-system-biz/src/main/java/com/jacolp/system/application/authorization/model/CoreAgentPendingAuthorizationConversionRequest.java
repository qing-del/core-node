package com.jacolp.system.application.authorization.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Untrusted application request to convert a retained pending authorization into a code. */
public record CoreAgentPendingAuthorizationConversionRequest(
        String rawPendingHandle,
        Long authenticatedUserId,
        String sessionId,
        String clientId,
        String redirectUri,
        String oauthState,
        List<String> grantedScopes) {

    public CoreAgentPendingAuthorizationConversionRequest {
        rawPendingHandle = IssuedCoreAgentAuthorizationPendingHandle.requireRawHandle(rawPendingHandle);
        if (authenticatedUserId == null || authenticatedUserId <= 0) {
            throw invalid();
        }
        sessionId = CoreAgentPendingAuthorizationState.requireSessionId(sessionId);
        if (!CoreAgentAuthorizationCodeState.CORE_AGENT_CLIENT_ID.equals(clientId)) {
            throw invalid();
        }
        redirectUri = CoreAgentAuthorizationCodeState.requireSafeRedirectUri(redirectUri);
        CoreAgentAuthorizationCodeState.requireSafeState(oauthState);
        grantedScopes = copy(grantedScopes);
    }

    @Override
    public String toString() {
        return "CoreAgentPendingAuthorizationConversionRequest[rawPendingHandle=<redacted>, authenticatedUserId="
                + "<redacted>, sessionId=<redacted>, clientId=" + clientId + ", redirectUri=<redacted>, oauthState="
                + "<redacted>, grantedScopes=<redacted>]";
    }

    private static List<String> copy(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            throw invalid();
        }
        List<String> copy = new ArrayList<>(values.size());
        for (String value : values) {
            if (value == null) {
                throw invalid();
            }
            copy.add(value);
        }
        return List.copyOf(copy);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid CORE AGENT pending authorization conversion request");
    }
}
