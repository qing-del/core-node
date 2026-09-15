package com.jacolp.common.security.oauth2.token;

import com.jacolp.common.security.oauth2.config.OAuth2Rs256Properties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Creates RS256 access-token claims. Callers must provide the canonical Phase 3 role value.
 */
public final class Rs256AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final OAuth2Rs256Properties properties;
    private final Clock clock;
    private final SecureOAuth2TokenGenerator tokenGenerator;

    public Rs256AccessTokenIssuer(JwtEncoder jwtEncoder, OAuth2Rs256Properties properties, Clock clock,
                                  SecureOAuth2TokenGenerator tokenGenerator) {
        this.jwtEncoder = Objects.requireNonNull(jwtEncoder, "jwtEncoder must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator must not be null");
    }

    public IssuedAccessToken issue(AccessTokenIssueRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(request.tokenTtl());
        String jti = tokenGenerator.newJti();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(Long.toString(request.userId()))
                .audience(List.of(properties.getAudience()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(jti)
                .claim("client_id", request.clientId())
                .claim("grant_type", request.grantType())
                .claim("username", request.username())
                .claim("roles", List.of(request.role()))
                .claim("scope", request.scopes().stream().sorted().toList())
                .build();
        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build(), claims))
                .getTokenValue();
        return new IssuedAccessToken(tokenValue, jti, issuedAt, expiresAt);
    }
}
