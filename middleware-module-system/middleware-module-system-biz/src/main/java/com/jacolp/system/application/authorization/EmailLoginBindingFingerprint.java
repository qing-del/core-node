package com.jacolp.system.application.authorization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Stable non-secret SHA-256 binding fingerprints for email-code controls. */
@Component
public final class EmailLoginBindingFingerprint {
    public String email(String email) {
        return hash("email\0".getBytes(StandardCharsets.UTF_8), canonicalEmail(email).getBytes(StandardCharsets.UTF_8));
    }

    public String socketAddress(String address) {
        return hash("socket\0".getBytes(StandardCharsets.UTF_8), ClientAllowedIpPolicy.canonicalSocketAddress(address));
    }

    private static String canonicalEmail(String email) {
        if (email == null) {
            throw invalid();
        }
        String value = email.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > 100) {
            throw invalid();
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (Character.isWhitespace(character) || Character.isSpaceChar(character) || Character.isISOControl(character)) {
                throw invalid();
            }
        }
        return value;
    }

    private static String hash(byte[] domain, byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(domain);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(value));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid email login binding");
    }
}
