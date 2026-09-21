package com.jacolp.system.web.authorization;

import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.Set;

/** Internal, non-sensitive client-authentication provenance for the fixed token grants. */
public record CoreAgentPublicClientAuthenticationDetails(String grantType) {

    private static final Set<String> ALLOWED_GRANTS = Set.of(
            AuthorizationGrantType.AUTHORIZATION_CODE.getValue(), AuthorizationGrantType.REFRESH_TOKEN.getValue());

    public CoreAgentPublicClientAuthenticationDetails {
        if (!ALLOWED_GRANTS.contains(grantType)) {
            throw new IllegalArgumentException("grantType must be a supported CORE AGENT token grant");
        }
    }

    @Override
    public String toString() {
        return "CoreAgentPublicClientAuthenticationDetails[redacted]";
    }
}
