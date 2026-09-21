package com.jacolp.system.application.authorization;

import com.jacolp.common.security.oauth2.token.SecureOAuth2TokenGenerator;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationPendingHandle;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

/** Generates 256-bit Java-{@link java.security.SecureRandom} pending handles for browser sessions. */
@Service
public final class CoreAgentPendingAuthorizationHandleGenerator {

    private final SecureOAuth2TokenGenerator tokenGenerator;

    public CoreAgentPendingAuthorizationHandleGenerator(SecureOAuth2TokenGenerator tokenGenerator) {
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator");
    }

    public IssuedCoreAgentAuthorizationPendingHandle generate(Instant expiresAt) {
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt is required");
        }
        String rawHandle = tokenGenerator.newOpaqueToken();
        if (rawHandle == null) {
            throw new IllegalStateException("CORE AGENT pending-handle generator returned null");
        }
        return new IssuedCoreAgentAuthorizationPendingHandle(rawHandle, expiresAt);
    }
}
