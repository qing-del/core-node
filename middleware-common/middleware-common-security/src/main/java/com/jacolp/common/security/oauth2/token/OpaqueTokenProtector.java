package com.jacolp.common.security.oauth2.token;

import org.springframework.security.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.regex.Pattern;

/** Protects 256-bit opaque tokens with a non-secret lookup fingerprint and BCrypt verifier. */
public final class OpaqueTokenProtector {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");

    public OpaqueTokenProtection protect(String rawToken) {
        validateRawToken(rawToken);
        return new OpaqueTokenProtection(fingerprint(rawToken), BCrypt.hashpw(rawToken, BCrypt.gensalt()));
    }

    public String fingerprint(String rawToken) {
        validateRawToken(rawToken);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public boolean matches(String rawToken, String verifierHash) {
        validateRawToken(rawToken);
        if (verifierHash == null || verifierHash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawToken, verifierHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static void validateRawToken(String rawToken) {
        if (rawToken == null || !TOKEN_PATTERN.matcher(rawToken).matches()) {
            throw new IllegalArgumentException("opaque token must be a 43-character Base64URL value");
        }
        try {
            if (Base64.getUrlDecoder().decode(rawToken).length != 32) {
                throw new IllegalArgumentException("opaque token must decode to 32 bytes");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("opaque token must be a 43-character Base64URL value");
        }
    }
}
