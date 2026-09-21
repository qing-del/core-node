package com.jacolp.system.application.authorization.model;

import java.time.Instant;

/**
 * A newly issued 256-bit authorization code and its expiry.
 *
 * <p>The raw code is required by the redirect response but is excluded from diagnostics.</p>
 */
public record IssuedCoreAgentAuthorizationCode(String rawCode, Instant expiresAt) {

    public IssuedCoreAgentAuthorizationCode {
        CoreAgentAuthorizationCodeState.require256BitBase64Url(rawCode, "rawCode");
        if (expiresAt == null) {
            throw new IllegalArgumentException("Invalid issued CORE AGENT authorization code: expiresAt is required");
        }
    }

    @Override
    public String toString() {
        return "IssuedCoreAgentAuthorizationCode[rawCode=<redacted>, expiresAt=" + expiresAt + ']';
    }
}
