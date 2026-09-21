package com.jacolp.security.oauth2.key;

import com.jacolp.common.security.oauth2.key.RsaKeyMaterial;
import com.jacolp.common.security.oauth2.key.RsaPemKeyMaterialLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RsaPemKeyMaterialLoaderTest {

    private final RsaPemKeyMaterialLoader loader = new RsaPemKeyMaterialLoader();

    @Test
    void loadsMatchingExternalPkcs8AndX509KeysWithStableKid(@TempDir Path temporaryDirectory) throws Exception {
        KeyPair pair = rsaPair(2048);
        Resource privateResource = pemFile(temporaryDirectory, "private.pem", "PRIVATE KEY", pair.getPrivate().getEncoded());
        Resource publicResource = pemFile(temporaryDirectory, "public.pem", "PUBLIC KEY", pair.getPublic().getEncoded());

        RsaKeyMaterial first = loader.load(privateResource, publicResource);
        RsaKeyMaterial second = loader.load(privateResource, publicResource);

        assertThat(first.privateKey()).isInstanceOf(RSAPrivateKey.class);
        assertThat(first.publicKey()).isInstanceOf(RSAPublicKey.class);
        assertThat(first.keyId()).isEqualTo(second.keyId()).doesNotContain("=");
        assertThat(first.toString()).contains(first.keyId()).doesNotContain("PRIVATE KEY").doesNotContain("privateExponent");
    }

    @Test
    void loadsClasspathAndByteArrayResources(@TempDir Path temporaryDirectory) throws Exception {
        KeyPair pair = rsaPair(2048);
        Resource publicResource = pemFile(temporaryDirectory, "public.pem", "PUBLIC KEY", pair.getPublic().getEncoded());
        Path classpathDirectory = Files.createDirectory(temporaryDirectory.resolve("classpath"));
        Files.writeString(classpathDirectory.resolve("private.pem"),
                pemContents("PRIVATE KEY", pair.getPrivate().getEncoded()));
        Files.writeString(classpathDirectory.resolve("public.pem"),
                pemContents("PUBLIC KEY", pair.getPublic().getEncoded()));

        RsaKeyMaterial classpathMaterial;
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{classpathDirectory.toUri().toURL()}, getClass().getClassLoader())) {
            classpathMaterial = loader.load(
                    new ClassPathResource("private.pem", classLoader),
                    new ClassPathResource("public.pem", classLoader));
        }
        RsaKeyMaterial byteArrayMaterial = loader.load(
                new ByteArrayResource(pemContents("PRIVATE KEY", pair.getPrivate().getEncoded())
                        .getBytes(StandardCharsets.US_ASCII)),
                publicResource);

        assertThat(classpathMaterial.privateKey()).isInstanceOf(RSAPrivateKey.class);
        assertThat(classpathMaterial.publicKey()).isInstanceOf(RSAPublicKey.class);
        assertThat(byteArrayMaterial.privateKey()).isInstanceOf(RSAPrivateKey.class);
    }

    @Test
    void rejectsNullAndUnreadableResourcesWithClearMessages(@TempDir Path temporaryDirectory) throws Exception {
        KeyPair pair = rsaPair(2048);
        Resource privateResource = pemFile(temporaryDirectory, "private.pem", "PRIVATE KEY", pair.getPrivate().getEncoded());
        Resource publicResource = pemFile(temporaryDirectory, "public.pem", "PUBLIC KEY", pair.getPublic().getEncoded());
        Resource missingResource = new FileSystemResource(temporaryDirectory.resolve("missing.pem"));

        assertThatThrownBy(() -> loader.load(null, publicResource))
                .hasMessage("RSA private key resource is required");
        assertThatThrownBy(() -> loader.load(privateResource, null))
                .hasMessage("RSA public key resource is required");
        assertThatThrownBy(() -> loader.load(missingResource, publicResource))
                .hasMessage("Unable to read RSA private key resource");
    }

    @Test
    void rejectsInvalidPemAndWrongKeyFormats(@TempDir Path temporaryDirectory) throws Exception {
        KeyPair pair = rsaPair(2048);
        Resource publicResource = pemFile(temporaryDirectory, "public.pem", "PUBLIC KEY", pair.getPublic().getEncoded());
        Resource invalidPem = textFile(temporaryDirectory, "invalid.pem", "not a PEM");
        Resource pkcs8AsPublic = pemFile(temporaryDirectory, "wrong-public.pem", "PUBLIC KEY", pair.getPrivate().getEncoded());

        assertThatThrownBy(() -> loader.load(invalidPem, publicResource))
                .hasMessage("Invalid RSA private PEM format");
        assertThatThrownBy(() -> loader.load(pemFile(temporaryDirectory, "private.pem", "PRIVATE KEY", pair.getPrivate().getEncoded()), pkcs8AsPublic))
                .hasMessage("Invalid X.509 RSA public key");
    }

    @Test
    void rejectsNonRsaShortAndMismatchedPairs(@TempDir Path temporaryDirectory) throws Exception {
        KeyPair ecPair = KeyPairGenerator.getInstance("EC").generateKeyPair();
        KeyPair shortPair = rsaPair(1024);
        KeyPair first = rsaPair(2048);
        KeyPair second = rsaPair(2048);

        assertThatThrownBy(() -> loader.load(
                pemFile(temporaryDirectory, "ec-private.pem", "PRIVATE KEY", ecPair.getPrivate().getEncoded()),
                pemFile(temporaryDirectory, "ec-public.pem", "PUBLIC KEY", ecPair.getPublic().getEncoded())))
                .hasMessage("Invalid PKCS#8 RSA private key");
        assertThatThrownBy(() -> loader.load(
                pemFile(temporaryDirectory, "short-private.pem", "PRIVATE KEY", shortPair.getPrivate().getEncoded()),
                pemFile(temporaryDirectory, "short-public.pem", "PUBLIC KEY", shortPair.getPublic().getEncoded())))
                .hasMessage("RSA private key modulus must be at least 2048 bits");
        assertThatThrownBy(() -> loader.load(
                pemFile(temporaryDirectory, "first-private.pem", "PRIVATE KEY", first.getPrivate().getEncoded()),
                pemFile(temporaryDirectory, "second-public.pem", "PUBLIC KEY", second.getPublic().getEncoded())))
                .hasMessage("RSA key pair modulus mismatch");
    }

    private static KeyPair rsaPair(int modulusBits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(modulusBits);
        return generator.generateKeyPair();
    }

    private static Resource pemFile(Path directory, String name, String type, byte[] encoded) throws Exception {
        return textFile(directory, name, pemContents(type, encoded));
    }

    private static String pemContents(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(encoded)
                + "\n-----END " + type + "-----\n";
    }

    private static Resource textFile(Path directory, String name, String text) throws Exception {
        Path path = Files.writeString(directory.resolve(name), text);
        return new FileSystemResource(path);
    }
}
