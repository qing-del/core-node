package com.jacolp.security.oauth2.key;

import com.jacolp.common.security.oauth2.key.PublicJwkSetProvider;
import com.jacolp.common.security.oauth2.key.RsaKeyMaterial;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicJwkSetProviderTest {

    @Test
    @SuppressWarnings("unchecked")
    void exportsOnlyOneStandardPublicRs256SigningKey() throws Exception {
        KeyPair pair = rsaPair();
        RsaKeyMaterial keyMaterial = new RsaKeyMaterial(
                (RSAPrivateKey) pair.getPrivate(), (RSAPublicKey) pair.getPublic(), "stable-key-id");
        PublicJwkSetProvider provider = new PublicJwkSetProvider(keyMaterial);

        JWKSet first = provider.publicJwkSet();
        JWKSet second = provider.publicJwkSet();
        JWK jwk = first.getKeyByKeyId(keyMaterial.keyId());
        Map<String, Object> exported = (Map<String, Object>) ((List<?>) first.toJSONObject().get("keys")).getFirst();

        assertThat(second).isNotSameAs(first);
        assertThatThrownBy(() -> first.getKeys().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(jwk.getKeyType().getValue()).isEqualTo("RSA");
        assertThat(jwk.getKeyUse().getValue()).isEqualTo("sig");
        assertThat(jwk.getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(jwk.getKeyID()).isEqualTo(keyMaterial.keyId());
        assertThat(exported).containsKeys("kty", "use", "alg", "kid", "n", "e");
        assertThat((String) exported.get("n")).isNotBlank();
        assertThat((String) exported.get("e")).isNotBlank();
        assertThat(exported).doesNotContainKeys("d", "p", "q", "dp", "dq", "qi", "oth");
        assertThat(provider).hasToString("PublicJwkSetProvider[keyCount=1]");
    }

    private static KeyPair rsaPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
