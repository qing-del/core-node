package com.jacolp.security.oauth2.token;

import com.jacolp.common.security.oauth2.config.OAuth2Rs256Properties;
import com.jacolp.common.security.oauth2.token.AccessTokenIssueRequest;
import com.jacolp.common.security.oauth2.token.IssuedAccessToken;
import com.jacolp.common.security.oauth2.token.Rs256AccessTokenIssuer;
import com.jacolp.common.security.oauth2.token.SecureOAuth2TokenGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class Rs256AccessTokenIssuerTest {

    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void issuesCompleteRs256ClaimsWithSortedWildcardScopesAndExactTtl() {
        AtomicReference<JwtEncoderParameters> captured = new AtomicReference<>();
        Rs256AccessTokenIssuer issuer = issuer(captured);
        AccessTokenIssueRequest request = new AccessTokenIssueRequest(42L, "user_client", "password", "alice",
                "USER", new LinkedHashSet<>(List.of("note:read", "*:read", "note:read")), Duration.ofMinutes(5));

        IssuedAccessToken issued = issuer.issue(request);
        JwtEncoderParameters parameters = captured.get();

        assertThat(issued.tokenValue()).isEqualTo("encoded-token");
        assertThat(issued.jti()).isEqualTo("AAECAwQFBgcICQoLDA0ODw");
        assertThat(issued.issuedAt()).isEqualTo(NOW);
        assertThat(issued.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(parameters.getJwsHeader().getHeaders()).containsEntry("alg", org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                .containsEntry("typ", "JWT");
        assertThat(parameters.getClaims().getClaims()).containsEntry("iss", "core-node")
                .containsEntry("sub", "42").containsEntry("aud", List.of("core-node-api"))
                .containsEntry("jti", issued.jti()).containsEntry("client_id", "user_client")
                .containsEntry("grant_type", "password").containsEntry("username", "alice")
                .containsEntry("roles", List.of("USER")).containsEntry("scope", List.of("*:read", "note:read"));
        assertThat(parameters.getClaims().getIssuedAt()).isEqualTo(NOW);
        assertThat(parameters.getClaims().getExpiresAt()).isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    void preservesEmptyScopeArrayAndDefensivelyCopiesInput() {
        AtomicReference<JwtEncoderParameters> captured = new AtomicReference<>();
        Set<String> input = new LinkedHashSet<>();
        AccessTokenIssueRequest request = new AccessTokenIssueRequest(1, "client", "authorization_code", "bob",
                "creator", input, Duration.ofSeconds(1));
        input.add("late:scope");

        issuer(captured).issue(request);

        assertThat(request.scopes()).isEmpty();
        assertThat(captured.get().getClaims().getClaimAsStringList("scope")).isEmpty();
        assertThatThrownBy(() -> request.scopes().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidRequestValues() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AccessTokenIssueRequest(0, "client", "grant", "user", "USER", Set.of(), Duration.ofSeconds(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new AccessTokenIssueRequest(1, " ", "grant", "user", "USER", Set.of(), Duration.ofSeconds(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new AccessTokenIssueRequest(1, "client", "grant", "user", " ", Set.of(), Duration.ofSeconds(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new AccessTokenIssueRequest(1, "client", "grant", "user", "USER", Set.of(" "), Duration.ofSeconds(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new AccessTokenIssueRequest(1, "client", "grant", "user", "USER", Set.of(), Duration.ZERO));
    }

    private static Rs256AccessTokenIssuer issuer(AtomicReference<JwtEncoderParameters> captured) {
        JwtEncoder encoder = parameters -> {
            captured.set(parameters);
            return new Jwt("encoded-token", NOW, NOW.plusSeconds(300),
                    parameters.getJwsHeader().getHeaders(), parameters.getClaims().getClaims());
        };
        OAuth2Rs256Properties properties = new OAuth2Rs256Properties();
        return new Rs256AccessTokenIssuer(encoder, properties, Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureOAuth2TokenGenerator(new DeterministicSecureRandom()));
    }

    private static final class DeterministicSecureRandom extends SecureRandom {
        @Override
        public void nextBytes(byte[] bytes) {
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = (byte) index;
            }
        }
    }
}
