package com.jacolp.security.oauth2.jwt;

import com.jacolp.common.security.oauth2.jwt.AccessTokenBlacklistJwtValidator;
import com.jacolp.common.security.oauth2.token.AccessTokenBlacklistStore;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AccessTokenBlacklistJwtValidatorTest {
    private static final String JTI = "AAECAwQFBgcICQoLDA0ODw";

    @Test void acceptsTokenNotInBlacklist() {
        AccessTokenBlacklistStore store = mock(AccessTokenBlacklistStore.class);
        when(store.isBlacklisted(JTI)).thenReturn(false);
        OAuth2TokenValidatorResult result = new AccessTokenBlacklistJwtValidator(store).validate(jwt(JTI));
        assertThat(result.hasErrors()).isFalse(); verify(store).isBlacklisted(JTI);
    }

    @Test void rejectsNullOrInvalidJtiWithoutStoreCall() {
        AccessTokenBlacklistStore store = mock(AccessTokenBlacklistStore.class);
        AccessTokenBlacklistJwtValidator validator = new AccessTokenBlacklistJwtValidator(store);
        assertError(validator.validate(null), "JWT identifier is missing or invalid");
        assertError(validator.validate(jwt(null)), "JWT identifier is missing or invalid");
        assertError(validator.validate(jwt("bad")), "JWT identifier is missing or invalid");
        verifyNoInteractions(store);
    }

    @Test void rejectsRevokedToken() {
        AccessTokenBlacklistStore store = mock(AccessTokenBlacklistStore.class);
        when(store.isBlacklisted(JTI)).thenReturn(true);
        assertError(new AccessTokenBlacklistJwtValidator(store).validate(jwt(JTI)), "JWT access has been revoked");
        verify(store).isBlacklisted(JTI);
    }

    @Test void failsClosedWhenBlacklistStoreUnavailable() {
        AccessTokenBlacklistStore store = mock(AccessTokenBlacklistStore.class);
        when(store.isBlacklisted(JTI)).thenThrow(new IllegalStateException("redis unavailable"));
        assertError(new AccessTokenBlacklistJwtValidator(store).validate(jwt(JTI)), "JWT revocation status is unavailable");
        verify(store).isBlacklisted(JTI);
    }

    private static void assertError(OAuth2TokenValidatorResult result, String description) {
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).singleElement().extracting(OAuth2Error::getErrorCode, OAuth2Error::getDescription)
                .containsExactly("invalid_token", description);
    }

    private static Jwt jwt(String jti) {
        Map<String, Object> claims = jti == null ? Map.of("sub", "user") : Map.of("sub", "user", "jti", jti);
        return new Jwt("token", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T01:00:00Z"), Map.of("alg", "RS256"), claims);
    }
}
