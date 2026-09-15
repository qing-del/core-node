package com.jacolp.common.security.oauth2.key;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import java.util.List;
import java.util.Objects;

/**
 * Read-only public JWK set for future key-discovery endpoints.
 *
 * <p>The signing private key is deliberately never provided to Nimbus from this type.
 * Each call returns a fresh set built only from the public JWK, so callers cannot retain
 * mutable provider state.</p>
 */
public final class PublicJwkSetProvider {

    private final RSAKey publicSigningKey;

    public PublicJwkSetProvider(RsaKeyMaterial keyMaterial) {
        Objects.requireNonNull(keyMaterial, "keyMaterial must not be null");
        this.publicSigningKey = new RSAKey.Builder(keyMaterial.publicKey())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID(keyMaterial.keyId())
                .build();
    }

    /** Returns a defensive standard JWK set containing the single public RS256 signing key. */
    public JWKSet publicJwkSet() {
        return new JWKSet(List.of(publicSigningKey));
    }

    @Override
    public String toString() {
        return "PublicJwkSetProvider[keyCount=1]";
    }
}
