package com.jacolp.common.security.oauth2.key;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Objects;

/** Immutable RS256 signing material. Its textual representation never exposes private-key material. */
public final class RsaKeyMaterial {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;

    public RsaKeyMaterial(RSAPrivateKey privateKey, RSAPublicKey publicKey, String keyId) {
        this.privateKey = Objects.requireNonNull(privateKey, "privateKey must not be null");
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey must not be null");
        this.keyId = Objects.requireNonNull(keyId, "keyId must not be null");
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public String keyId() {
        return keyId;
    }

    @Override
    public String toString() {
        return "RsaKeyMaterial[keyId=" + keyId + ", modulusBits=" + publicKey.getModulus().bitLength() + "]";
    }
}
