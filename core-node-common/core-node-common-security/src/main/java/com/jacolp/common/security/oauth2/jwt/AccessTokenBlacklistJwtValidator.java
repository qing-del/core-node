package com.jacolp.common.security.oauth2.jwt;

import com.jacolp.common.security.oauth2.token.AccessTokenBlacklistStore;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Objects;
import java.util.regex.Pattern;

/** Rejects access tokens whose identifiers have been revoked. */
public final class AccessTokenBlacklistJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final Pattern JTI_PATTERN = Pattern.compile("[A-Za-z0-9_-]{22}");
    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error("invalid_token", "JWT identifier is missing or invalid", null);
    private static final OAuth2Error REVOKED_TOKEN = new OAuth2Error("invalid_token", "JWT access has been revoked", null);
    private static final OAuth2Error BLACKLIST_UNAVAILABLE = new OAuth2Error("invalid_token", "JWT revocation status is unavailable", null);

    private final AccessTokenBlacklistStore blacklistStore;

    public AccessTokenBlacklistJwtValidator(AccessTokenBlacklistStore blacklistStore) {
        this.blacklistStore = Objects.requireNonNull(blacklistStore, "blacklistStore must not be null");
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String jti = jwt == null ? null : jwt.getId();
        if (jti == null || !JTI_PATTERN.matcher(jti).matches()) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
        try {
            return blacklistStore.isBlacklisted(jti)
                    ? OAuth2TokenValidatorResult.failure(REVOKED_TOKEN)
                    : OAuth2TokenValidatorResult.success();
        } catch (RuntimeException exception) {
            return OAuth2TokenValidatorResult.failure(BLACKLIST_UNAVAILABLE);
        }
    }
}
