package com.jacolp.common.security.oauth2.config;

import com.jacolp.common.security.oauth2.jwt.AccessTokenBlacklistJwtValidator;
import com.jacolp.common.security.oauth2.jwt.CoreNodeAccessTokenClaimsValidator;
import com.jacolp.common.security.oauth2.jwt.RequiredAudienceJwtValidator;
import com.jacolp.common.security.oauth2.key.PublicJwkSetProvider;
import com.jacolp.common.security.oauth2.key.RsaKeyMaterial;
import com.jacolp.common.security.oauth2.key.RsaPemKeyMaterialLoader;
import com.jacolp.common.security.oauth2.token.Rs256AccessTokenIssuer;
import com.jacolp.common.security.oauth2.token.SecureOAuth2TokenGenerator;
import com.jacolp.common.security.oauth2.token.AccessTokenBlacklistStore;
import com.jacolp.common.security.oauth2.token.RedisAccessTokenBlacklistStore;
import com.jacolp.common.security.oauth2.token.OAuth2RefreshTokenSessionService;
import com.jacolp.common.security.oauth2.token.OAuth2SessionRevocationStore;
import com.jacolp.common.security.oauth2.token.OAuth2TokenStateCodec;
import com.jacolp.common.security.oauth2.token.OAuth2TokenStateStore;
import com.jacolp.common.security.oauth2.token.OpaqueTokenProtector;
import com.jacolp.common.security.oauth2.token.RedisOAuth2TokenStateStore;
import com.jacolp.common.security.oauth2.token.RedisOAuth2SessionRevocationStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

/**
 * RS256 codec infrastructure required for the OAuth2 token flow.
 */
@Configuration(proxyBeanMethods = false)
public class OAuth2Rs256CodecConfiguration {

    @Bean
    RsaKeyMaterial oauth2Rs256KeyMaterial(OAuth2Rs256Properties properties) {
        return new RsaPemKeyMaterialLoader().load(
                properties.getPrivateKeyLocation(), properties.getPublicKeyLocation());
    }

    @Bean
    PublicJwkSetProvider publicJwkSetProvider(RsaKeyMaterial keyMaterial) {
        return new PublicJwkSetProvider(keyMaterial);
    }

    @Bean
    JwtEncoder oauth2Rs256JwtEncoder(RsaKeyMaterial keyMaterial) {
        return NimbusJwtEncoder.withKeyPair(keyMaterial.publicKey(), keyMaterial.privateKey())
                .algorithm(SignatureAlgorithm.RS256)
                .jwkPostProcessor(builder -> builder.keyID(keyMaterial.keyId()))
                .build();
    }

    @Bean
    JwtDecoder oauth2Rs256JwtDecoder(RsaKeyMaterial keyMaterial, OAuth2Rs256Properties properties,
                                     AccessTokenBlacklistJwtValidator blacklistValidator,
                                     CoreNodeAccessTokenClaimsValidator accessTokenClaimsValidator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keyMaterial.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.getIssuer()),
                new RequiredAudienceJwtValidator(properties.getAudience()), blacklistValidator, accessTokenClaimsValidator));
        return decoder;
    }

    @Bean
    SecureOAuth2TokenGenerator secureOAuth2TokenGenerator() {
        return new SecureOAuth2TokenGenerator();
    }

    @Bean
    OpaqueTokenProtector opaqueTokenProtector() {
        return new OpaqueTokenProtector();
    }

    @Bean
    OAuth2TokenStateCodec oauth2TokenStateCodec() {
        return new OAuth2TokenStateCodec();
    }

    @Bean
    OAuth2TokenStateStore oauth2TokenStateStore(StringRedisTemplate redis, OAuth2TokenStateCodec codec) {
        return new RedisOAuth2TokenStateStore(redis, codec);
    }

    @Bean
    OAuth2RefreshTokenSessionService oauth2RefreshTokenSessionService(SecureOAuth2TokenGenerator tokenGenerator,
                                                                      OpaqueTokenProtector tokenProtector,
                                                                      OAuth2TokenStateStore stateStore) {
        return new OAuth2RefreshTokenSessionService(Clock.systemUTC(), tokenGenerator, tokenProtector, stateStore);
    }

    @Bean
    AccessTokenBlacklistStore accessTokenBlacklistStore(StringRedisTemplate redis) {
        return new RedisAccessTokenBlacklistStore(redis);
    }

    @Bean
    OAuth2SessionRevocationStore oauth2SessionRevocationStore(StringRedisTemplate redis) {
        return new RedisOAuth2SessionRevocationStore(redis);
    }

    @Bean
    AccessTokenBlacklistJwtValidator accessTokenBlacklistJwtValidator(AccessTokenBlacklistStore blacklistStore) {
        return new AccessTokenBlacklistJwtValidator(blacklistStore);
    }

    @Bean
    CoreNodeAccessTokenClaimsValidator coreNodeAccessTokenClaimsValidator() {
        return new CoreNodeAccessTokenClaimsValidator();
    }

    @Bean
    Rs256AccessTokenIssuer rs256AccessTokenIssuer(JwtEncoder jwtEncoder, OAuth2Rs256Properties properties,
                                                   SecureOAuth2TokenGenerator tokenGenerator) {
        return new Rs256AccessTokenIssuer(jwtEncoder, properties, Clock.systemUTC(), tokenGenerator);
    }
}
