package com.jacolp.system.infrastructure.security;

import com.jacolp.system.application.port.out.EmailLoginCodeProtector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** BCrypt implementation with a dummy comparison for absent verifiers. */
@Component
public final class BCryptEmailLoginCodeProtector implements EmailLoginCodeProtector {
    private static final String DUMMY_RAW_CODE = "email-login-code-dummy";
    private final PasswordEncoder passwordEncoder;
    private final String dummyVerifier;

    @Autowired
    public BCryptEmailLoginCodeProtector(PasswordEncoder passwordEncoder) {
        this(passwordEncoder, passwordEncoder.encode(DUMMY_RAW_CODE));
    }

    BCryptEmailLoginCodeProtector(PasswordEncoder passwordEncoder, String dummyVerifier) {
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        if (dummyVerifier == null || dummyVerifier.isBlank()) {
            throw new IllegalArgumentException("dummyVerifier must not be blank");
        }
        this.dummyVerifier = dummyVerifier;
    }

    @Override
    public String protect(String rawCode) {
        requireCode(rawCode);
        return passwordEncoder.encode(rawCode);
    }

    @Override
    public boolean matches(String rawCode, String verifier) {
        if (!isCode(rawCode)) {
            return false;
        }
        try {
            return passwordEncoder.matches(rawCode, verifier == null ? dummyVerifier : verifier);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static void requireCode(String rawCode) {
        if (!isCode(rawCode)) {
            throw new IllegalArgumentException("Email login code must be exactly six decimal digits");
        }
    }

    private static boolean isCode(String rawCode) {
        if (rawCode == null || rawCode.length() != 6) {
            return false;
        }
        for (int index = 0; index < rawCode.length(); index++) {
            char character = rawCode.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }
}
