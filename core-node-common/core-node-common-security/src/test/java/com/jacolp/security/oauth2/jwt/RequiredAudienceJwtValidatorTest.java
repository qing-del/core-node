package com.jacolp.security.oauth2.jwt;

import com.jacolp.common.security.oauth2.jwt.RequiredAudienceJwtValidator;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RequiredAudienceJwtValidatorTest {

    private final RequiredAudienceJwtValidator validator = new RequiredAudienceJwtValidator("core-node-api");

    @Test
    void acceptsJwtWithExactRequiredAudience() {
        OAuth2TokenValidatorResult result = validator.validate(jwt(List.of("core-node-api")));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void acceptsJwtWithRequiredAudienceAmongMultipleAudiences() {
        OAuth2TokenValidatorResult result = validator.validate(jwt(List.of("other-api", "core-node-api")));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsMissingAndDifferentAudiencesWithStableError() {
        assertInvalidAudience(validator.validate(jwtWithoutAudience()));
        assertInvalidAudience(validator.validate(jwt(List.of("other-api"))));
        assertInvalidAudience(validator.validate(null));
    }

    @Test
    void rejectsBlankRequiredAudience() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RequiredAudienceJwtValidator(null))
                .withMessage("Required JWT audience must not be blank");
        assertThatIllegalArgumentException().isThrownBy(() -> new RequiredAudienceJwtValidator("   "))
                .withMessage("Required JWT audience must not be blank");
    }

    private static void assertInvalidAudience(OAuth2TokenValidatorResult result) {
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .singleElement()
                .extracting(OAuth2Error::getErrorCode, OAuth2Error::getDescription, OAuth2Error::getUri)
                .containsExactly("invalid_token", "JWT audience is missing or invalid", null);
    }

    private static Jwt jwt(List<String> audience) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T01:00:00Z"))
                .audience(audience)
                .build();
    }

    private static Jwt jwtWithoutAudience() {
        return new Jwt(
                "test-token",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T01:00:00Z"),
                Map.of("alg", "RS256"),
                Map.of("sub", "test-subject"));
    }
}
