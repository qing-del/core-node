package com.jacolp.common.security.oauth2.key;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Loads PKCS#8/X.509 RSA PEM resources without registering runtime security infrastructure. */
public final class RsaPemKeyMaterialLoader {

    private static final int MINIMUM_RSA_MODULUS_BITS = 2048;

    public RsaKeyMaterial load(Resource privateKeyResource, Resource publicKeyResource) {
        byte[] privateKeyDer = decodePem(readResource(privateKeyResource, "private"),
                "-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----", "private");
        byte[] publicKeyDer = decodePem(readResource(publicKeyResource, "public"),
                "-----BEGIN PUBLIC KEY-----", "-----END PUBLIC KEY-----", "public");
        RSAPrivateKey privateKey = toRsaPrivateKey(privateKeyDer);
        RSAPublicKey publicKey = toRsaPublicKey(publicKeyDer);

        requireMinimumModulus(privateKey.getModulus().bitLength(), "private");
        requireMinimumModulus(publicKey.getModulus().bitLength(), "public");
        if (!privateKey.getModulus().equals(publicKey.getModulus())) {
            throw new IllegalArgumentException("RSA key pair modulus mismatch");
        }
        return new RsaKeyMaterial(privateKey, publicKey, keyId(publicKeyDer));
    }

    private static byte[] readResource(Resource resource, String keyType) {
        if (resource == null) {
            throw new IllegalArgumentException("RSA " + keyType + " key resource is required");
        }
        try {
            try (var input = resource.getInputStream()) {
                return input.readAllBytes();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read RSA " + keyType + " key resource", exception);
        }
    }

    private static byte[] decodePem(byte[] contents, String beginMarker, String endMarker, String keyType) {
        String pem = new String(contents, StandardCharsets.US_ASCII);
        int begin = pem.indexOf(beginMarker);
        int end = pem.indexOf(endMarker);
        if (begin != 0 || end <= begin || !pem.substring(end + endMarker.length()).trim().isEmpty()) {
            throw invalidPem(keyType);
        }
        String body = pem.substring(beginMarker.length(), end).replaceAll("\\s", "");
        if (body.isEmpty() || !body.matches("[A-Za-z0-9+/=]+")) {
            throw invalidPem(keyType);
        }
        try {
            return Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException exception) {
            throw invalidPem(keyType);
        }
    }

    private static RSAPrivateKey toRsaPrivateKey(byte[] der) {
        try {
            PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            if (privateKey instanceof RSAPrivateKey rsaPrivateKey) {
                return rsaPrivateKey;
            }
        } catch (GeneralSecurityException ignored) {
            // Re-throw the intentionally content-free message below.
        }
        throw new IllegalArgumentException("Invalid PKCS#8 RSA private key");
    }

    private static RSAPublicKey toRsaPublicKey(byte[] der) {
        try {
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            if (publicKey instanceof RSAPublicKey rsaPublicKey) {
                return rsaPublicKey;
            }
        } catch (GeneralSecurityException ignored) {
            // Re-throw the intentionally content-free message below.
        }
        throw new IllegalArgumentException("Invalid X.509 RSA public key");
    }

    private static void requireMinimumModulus(int modulusBits, String keyType) {
        if (modulusBits < MINIMUM_RSA_MODULUS_BITS) {
            throw new IllegalArgumentException("RSA " + keyType + " key modulus must be at least 2048 bits");
        }
    }

    private static String keyId(byte[] publicKeyDer) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKeyDer);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static IllegalArgumentException invalidPem(String keyType) {
        return new IllegalArgumentException("Invalid RSA " + keyType + " PEM format");
    }
}
