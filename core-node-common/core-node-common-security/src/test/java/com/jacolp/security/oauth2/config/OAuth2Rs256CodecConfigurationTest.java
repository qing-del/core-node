package com.jacolp.security.oauth2.config;

import com.jacolp.common.security.oauth2.config.OAuth2Rs256CodecConfiguration;
import com.jacolp.common.security.oauth2.config.OAuth2Rs256Properties;
import com.jacolp.common.security.oauth2.key.PublicJwkSetProvider;
import com.jacolp.common.security.oauth2.key.RsaKeyMaterial;
import com.jacolp.common.security.oauth2.token.AccessTokenIssueRequest;
import com.jacolp.common.security.oauth2.token.AccessTokenSessionReference;
import com.jacolp.common.security.oauth2.token.IssuedAccessToken;
import com.jacolp.common.security.oauth2.token.IssuedRefreshToken;
import com.jacolp.common.security.oauth2.token.OAuth2RefreshTokenSessionService;
import com.jacolp.common.security.oauth2.token.OAuth2TokenStateCodec;
import com.jacolp.common.security.oauth2.token.OAuth2TokenStateStore;
import com.jacolp.common.security.oauth2.token.OAuth2SessionRevocationStore;
import com.jacolp.common.security.oauth2.token.RedisOAuth2TokenStateStore;
import com.jacolp.common.security.oauth2.token.RefreshTokenIssueRequest;
import com.jacolp.common.security.oauth2.token.Rs256AccessTokenIssuer;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class OAuth2Rs256CodecConfigurationTest {

    @TempDir
    static Path keyDirectory;

    private static KeyPair signingPair;
    private static KeyPair differentPair;
    private static Path privateKey;
    private static Path publicKey;
    private static Path invalidKey;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class, OAuth2Rs256CodecConfiguration.class);

    @BeforeAll
    static void createKeyFixtures() throws Exception {
        signingPair = rsaPair();
        differentPair = rsaPair();
        privateKey = pemFile("private.pem", "PRIVATE KEY", signingPair.getPrivate().getEncoded());
        publicKey = pemFile("public.pem", "PUBLIC KEY", signingPair.getPublic().getEncoded());
        invalidKey = Files.writeString(keyDirectory.resolve("invalid.pem"), "not a PEM");
    }

    @Test
    void configuredPemMaterialCreatesRs256BeansAndEncodesWithStableKid() {
        configuredContext().run(context -> {
            assertThat(context.getBeansOfType(OAuth2SessionRevocationStore.class)).hasSize(1);
            JwtEncoder encoder = context.getBean(JwtEncoder.class);
            JwtDecoder decoder = context.getBean(JwtDecoder.class);
            RsaKeyMaterial keyMaterial = context.getBean(RsaKeyMaterial.class);

            Jwt encoded = encoder.encode(JwtEncoderParameters.from(validClaims()));
            Jwt decoded = decoder.decode(encoded.getTokenValue());

            assertThat(encoded.getHeaders())
                    .containsEntry("alg", SignatureAlgorithm.RS256)
                    .containsEntry("kid", keyMaterial.keyId());
            assertThat(decoded.getHeaders())
                    .containsEntry("alg", "RS256")
                    .containsEntry("kid", keyMaterial.keyId());
            assertThat(decoded.getAudience()).containsExactly("core-node-api");
            assertThat(context.getBean(PublicJwkSetProvider.class).publicJwkSet().getKeyByKeyId(keyMaterial.keyId()))
                    .isNotNull();
        });
    }

    @Test
    void configuredPemMaterialIssuesAndDecodesCompleteAccessToken() {
        configuredContext().run(context -> {
            IssuedAccessToken issued = context.getBean(Rs256AccessTokenIssuer.class).issue(
                    new AccessTokenIssueRequest(42, "user", "password", "alice", "USER",
                            Set.of("*:read", "note:read"), Duration.ofMinutes(5)));
            Jwt decoded = context.getBean(JwtDecoder.class).decode(issued.tokenValue());

            assertThat(decoded.getId()).isEqualTo(issued.jti());
            assertThat(decoded.getSubject()).isEqualTo("42");
            assertThat(decoded.getClaimAsString("client_id")).isEqualTo("user");
            assertThat(decoded.getClaimAsStringList("roles")).containsExactly("USER");
            assertThat(decoded.getClaimAsStringList("scope")).containsExactly("*:read", "note:read");
            assertThat(Duration.between(issued.issuedAt(), issued.expiresAt())).isEqualTo(Duration.ofMinutes(5));
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void configuredPemMaterialWiresRefreshSessionServiceThroughRedisLuaWithoutPersistingRawToken() {
        configuredContext().run(context -> {
            StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
            doReturn(1L).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));

            assertThat(context.getBean(OAuth2TokenStateStore.class)).isInstanceOf(RedisOAuth2TokenStateStore.class);
            assertThat(context.getBean(OAuth2TokenStateCodec.class)).isNotNull();
            IssuedRefreshToken issued = context.getBean(OAuth2RefreshTokenSessionService.class).issue(
                    new RefreshTokenIssueRequest(7, "user_client", List.of("note:read"),
                            new AccessTokenSessionReference("AAECAwQFBgcICQoLDA0ODw", Instant.now().plusSeconds(60)),
                            Duration.ofMinutes(2)));

            ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass((Class) RedisScript.class);
            ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
            verify(redis).execute(scriptCaptor.capture(), anyList(), argumentsCaptor.capture());
            assertThat(scriptCaptor.getValue().getScriptAsString()).contains("HSET", "PEXPIRE");
            assertThat(argumentsCaptor.getValue()).doesNotContain(issued.rawToken());
            assertThat(issued.toString()).doesNotContain(issued.rawToken()).contains("<redacted>");
        });
    }

    @Test
    void configuredPemMaterialRejectsBlacklistedOrUnavailableJti() {
        configuredContext().run(context -> {
            StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
            IssuedAccessToken issued = context.getBean(Rs256AccessTokenIssuer.class).issue(
                    new AccessTokenIssueRequest(1, "user", "password", "alice", "USER", Set.of(), Duration.ofMinutes(5)));
            JwtDecoder decoder = context.getBean(JwtDecoder.class);
            assertThat(decoder.decode(issued.tokenValue()).getId()).isEqualTo(issued.jti());
            when(redis.hasKey("user:blacklist:access:" + issued.jti())).thenReturn(true);
            assertThatThrownBy(() -> decoder.decode(issued.tokenValue())).isInstanceOf(JwtException.class);
            when(redis.hasKey("user:blacklist:access:" + issued.jti())).thenThrow(new IllegalStateException("redis down"));
            assertThatThrownBy(() -> decoder.decode(issued.tokenValue())).isInstanceOf(JwtException.class);
        });
    }

    @Test
    void configuredPemMaterialRejectsWrongSignature() {
        configuredContext().run(context -> {
            JwtEncoder otherEncoder = NimbusJwtEncoder.withKeyPair(
                            (java.security.interfaces.RSAPublicKey) differentPair.getPublic(),
                            (java.security.interfaces.RSAPrivateKey) differentPair.getPrivate())
                    .algorithm(SignatureAlgorithm.RS256)
                    .build();
            String token = otherEncoder.encode(JwtEncoderParameters.from(validClaims())).getTokenValue();

            assertThatThrownBy(() -> context.getBean(JwtDecoder.class).decode(token))
                    .isInstanceOf(JwtException.class);
        });
    }

    @Test
    void configuredPemMaterialRejectsWrongIssuerAudienceAndExpiredJwt() {
        configuredContext().run(context -> {
            JwtEncoder encoder = context.getBean(JwtEncoder.class);
            JwtDecoder decoder = context.getBean(JwtDecoder.class);

            assertThatThrownBy(() -> decoder.decode(encode(encoder, claims("other-issuer", "core-node-api", Instant.now().plusSeconds(300)))))
                    .isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> decoder.decode(encode(encoder, claims("core-node", "other-api", Instant.now().plusSeconds(300)))))
                    .isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> decoder.decode(encode(encoder, claims("core-node", "core-node-api", Instant.now().minusSeconds(300)))))
                    .isInstanceOf(JwtException.class);
        });
    }

    @Test
    void configuredPemMaterialRejectsHs256Jwt() throws Exception {
        configuredContext().run(context -> {
            try {
                assertThatThrownBy(() -> context.getBean(JwtDecoder.class).decode(hs256Token()))
                        .isInstanceOf(JwtException.class);
            } catch (Exception exception) {
                throw new AssertionError("Could not create HS256 test token", exception);
            }
        });
    }

    @Test
    void configurationFailsFastForMissingOrInvalidKeyMaterial() {
        contextRunner.withUserConfiguration(RedisConfiguration.class)
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
        contextRunner.withPropertyValues(
                        "jacolp.oauth2.rs256.private-key-location=",
                        "jacolp.oauth2.rs256.public-key-location=" + publicKey.toUri())
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
        contextRunner.withPropertyValues(
                        "jacolp.oauth2.rs256.private-key-location=" + invalidKey.toUri(),
                        "jacolp.oauth2.rs256.public-key-location=" + publicKey.toUri())
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    private ApplicationContextRunner configuredContext() {
        return contextRunner.withUserConfiguration(RedisConfiguration.class).withPropertyValues(
                "jacolp.oauth2.rs256.private-key-location=" + privateKey.toUri(),
                "jacolp.oauth2.rs256.public-key-location=" + publicKey.toUri());
    }

    private static String encode(JwtEncoder encoder, JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private static JwtClaimsSet validClaims() {
        return claims("core-node", "core-node-api", Instant.now().plusSeconds(300));
    }

    private static JwtClaimsSet claims(String issuer, String audience, Instant expiresAt) {
        Instant issuedAt = expiresAt.isBefore(Instant.now())
                ? expiresAt.minusSeconds(300)
                : Instant.now();
        return JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("42")
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id("AAECAwQFBgcICQoLDA0ODw")
                .claim("client_id", "user")
                .claim("grant_type", "password")
                .claim("username", "alice")
                .claim("roles", List.of("USER"))
                .claim("scope", List.of("*:read"))
                .build();
    }

    private static String hs256Token() throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new com.nimbusds.jwt.JWTClaimsSet.Builder()
                        .issuer("core-node")
                        .subject("test-subject")
                        .audience("core-node-api")
                        .issueTime(new Date())
                        .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                        .jwtID("test-jti")
                        .build());
        byte[] secret = new byte[32];
        Arrays.fill(secret, (byte) 1);
        jwt.sign(new MACSigner(secret));
        return jwt.serialize();
    }

    private static KeyPair rsaPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static Path pemFile(String name, String type, byte[] encoded) throws Exception {
        return Files.writeString(keyDirectory.resolve(name), "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                + "\n-----END " + type + "-----\n");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OAuth2Rs256Properties.class)
    static class PropertiesConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisConfiguration {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }
}
