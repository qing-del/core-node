package com.jacolp.common.security.oauth2.jwt;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

/**
 * Validates that a JWT contains the configured audience exactly.
 */
public final class RequiredAudienceJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            "invalid_token", "JWT audience is missing or invalid", null);

    private final String requiredAudience;

    public RequiredAudienceJwtValidator(String requiredAudience) {
        if (requiredAudience == null || requiredAudience.isBlank()) {
            throw new IllegalArgumentException("Required JWT audience must not be blank");
        }
        this.requiredAudience = requiredAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (jwt == null || !containsRequiredAudience(jwt.getAudience())) {
            return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private boolean containsRequiredAudience(Collection<String> audiences) {
        return audiences != null && audiences.contains(requiredAudience);
    }
}
