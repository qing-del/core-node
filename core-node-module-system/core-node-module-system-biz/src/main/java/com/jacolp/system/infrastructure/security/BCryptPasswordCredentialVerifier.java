package com.jacolp.system.infrastructure.security;

import com.jacolp.system.application.port.out.PasswordCredentialVerifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * BCrypt-backed credential verifier that performs a dummy BCrypt comparison for absent account hashes.
 */
@Component
public final class BCryptPasswordCredentialVerifier implements PasswordCredentialVerifier {

    private static final String DUMMY_RAW_PASSWORD = "authorization-account-dummy-password";

    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    @Autowired
    public BCryptPasswordCredentialVerifier(PasswordEncoder passwordEncoder) {
        this(passwordEncoder, passwordEncoder.encode(DUMMY_RAW_PASSWORD));
    }

    BCryptPasswordCredentialVerifier(PasswordEncoder passwordEncoder, String dummyPasswordHash) {
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        if (dummyPasswordHash == null || dummyPasswordHash.isBlank()) {
            throw new IllegalArgumentException("dummyPasswordHash must not be blank");
        }
        this.dummyPasswordHash = dummyPasswordHash;
    }

    @Override
    public boolean matches(String rawPassword, String storedPasswordHash) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return false;
        }
        try {
            return passwordEncoder.matches(rawPassword,
                    storedPasswordHash == null ? dummyPasswordHash : storedPasswordHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
