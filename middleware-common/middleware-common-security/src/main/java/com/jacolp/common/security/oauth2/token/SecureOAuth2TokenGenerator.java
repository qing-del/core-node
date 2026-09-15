package com.jacolp.common.security.oauth2.token;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Generates opaque OAuth2 identifiers with cryptographically secure random bytes.
 */
public final class SecureOAuth2TokenGenerator {

    private static final int JTI_BYTES = 16;
    private static final int OPAQUE_TOKEN_BYTES = 32;

    private final SecureRandom secureRandom;

    public SecureOAuth2TokenGenerator() {
        this(new SecureRandom());
    }

    public SecureOAuth2TokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    public String newJti() {
        return newTokenValue(JTI_BYTES);
    }

    public String newOpaqueToken() {
        return newTokenValue(OPAQUE_TOKEN_BYTES);
    }

    private String newTokenValue(int byteCount) {
        byte[] randomBytes = new byte[byteCount];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
