package com.jacolp.system.application.authorization.model;

import java.time.Instant;
import java.util.Base64;

/**
 * A browser-session handle for one uncompleted CORE AGENT authorization request.
 *
 * <p>The value is a 256-bit canonical Base64URL opaque value generated in Java from
 * {@link java.security.SecureRandom}. It is not an OAuth authorization code and its string form
 * deliberately never exposes the handle.</p>
 */
public record IssuedCoreAgentAuthorizationPendingHandle(String rawHandle, Instant expiresAt) {

    public IssuedCoreAgentAuthorizationPendingHandle {
        requireRawHandle(rawHandle);
        if (expiresAt == null) {
            throw invalid();
        }
    }

    /** Validates and returns one canonical 256-bit opaque pending handle. */
    public static String requireRawHandle(String rawHandle) {
        if (rawHandle == null || rawHandle.length() != 43) {
            throw invalid();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(rawHandle);
            if (decoded.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded)
                    .equals(rawHandle)) {
                throw invalid();
            }
            return rawHandle;
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    @Override
    public String toString() {
        return "IssuedCoreAgentAuthorizationPendingHandle[rawHandle=<redacted>, expiresAt=" + expiresAt + ']';
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid CORE AGENT authorization pending handle");
    }
}
