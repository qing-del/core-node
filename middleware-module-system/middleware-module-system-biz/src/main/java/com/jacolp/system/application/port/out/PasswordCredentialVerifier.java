package com.jacolp.system.application.port.out;

/**
 * Verifies a raw password against a stored BCrypt credential hash.
 */
public interface PasswordCredentialVerifier {

    /**
     * Returns {@code false} for invalid raw passwords, absent stored hashes, and malformed credential hashes.
     */
    boolean matches(String rawPassword, String storedPasswordHash);
}
