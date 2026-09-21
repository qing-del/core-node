package com.jacolp.common.security.oauth2.token;

/** Non-secret lookup fingerprint and BCrypt verifier for an opaque token. */
public final class OpaqueTokenProtection {
    private final String fingerprint;
    private final String verifierHash;

    OpaqueTokenProtection(String fingerprint, String verifierHash) {
        this.fingerprint = fingerprint;
        this.verifierHash = verifierHash;
    }

    public String fingerprint() { return fingerprint; }
    public String verifierHash() { return verifierHash; }

    @Override
    public String toString() {
        return "OpaqueTokenProtection[fingerprint=" + fingerprint + "]";
    }
}
