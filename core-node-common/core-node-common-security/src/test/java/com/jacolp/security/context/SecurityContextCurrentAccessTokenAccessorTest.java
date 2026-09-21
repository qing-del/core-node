package com.jacolp.security.context;

import com.jacolp.common.core.exception.AuthenticationException;
import com.jacolp.common.security.context.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityContextCurrentAccessTokenAccessorTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-08-11T01:00:00Z");
    private static final String JTI = "0123456789abcdefghijkl";

    private final CurrentAccessTokenAccessor accessor = new SecurityContextCurrentAccessTokenAccessor();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ComponentConfiguration.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mapsStrictJwtClaimsWithoutReadingTheBearerToken() {
        Jwt jwt = jwt(Map.of("sub", "7", "client_id", "user", "jti", JTI), EXPIRES_AT, null);
        authenticate(jwt);

        CurrentAccessTokenReference reference = accessor.currentAccessToken().orElseThrow();

        assertThat(reference.userId()).isEqualTo(7L);
        assertThat(reference.clientId()).isEqualTo("user");
        assertThat(reference.jti()).isEqualTo(JTI);
        assertThat(reference.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(reference.toString()).doesNotContain("raw.jwt.token");
        verify(jwt, never()).getTokenValue();
    }

    @Test
    void returnsEmptyForAbsentUnauthenticatedLegacyAndUnsupportedPrincipals() {
        assertThat(accessor.currentAccessToken()).isEmpty();

        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated("legacy", null));
        assertThat(accessor.currentAccessToken()).isEmpty();

        authenticate(new SecurityPrincipal(7L, SecurityIdentity.USER));
        assertThat(accessor.currentAccessToken()).isEmpty();

        authenticate("unsupported");
        assertThat(accessor.currentAccessToken()).isEmpty();
    }

    @Test
    void rejectsMalformedSubjectClientJtiAndExpiryClaims() {
        assertMalformed(jwt(Map.of("sub", "not-a-number", "client_id", "user", "jti", JTI), EXPIRES_AT, null), "sub");
        assertMalformed(jwt(Map.of("sub", "7", "client_id", "unsafe client", "jti", JTI), EXPIRES_AT, null), "client_id");
        assertMalformed(jwt(Map.of("sub", "7", "client_id", "user", "jti", "not-base64url"), EXPIRES_AT, null), "jti");
        Map<String, Object> nullJti = new HashMap<>(Map.of("sub", "7", "client_id", "user"));
        nullJti.put("jti", null);
        assertMalformed(jwt(nullJti, EXPIRES_AT, JTI), "jti");
        assertMalformed(jwt(Map.of("sub", "7", "client_id", "user"), EXPIRES_AT, "bad-jti"), "jti");
        assertMalformed(jwt(Map.of("sub", "7", "client_id", "user", "jti", JTI), null, null), "exp");
    }

    @Test
    void modelRejectsInvalidValuesAndDoesNotContainBearerToken() {
        CurrentAccessTokenReference reference = new CurrentAccessTokenReference(7L, "user", JTI, EXPIRES_AT);

        assertThat(reference.toString()).doesNotContain("raw.jwt.token");
        assertThatIllegalArgumentException().isThrownBy(() -> new CurrentAccessTokenReference(0, "user", JTI, EXPIRES_AT));
        assertThatIllegalArgumentException().isThrownBy(() -> new CurrentAccessTokenReference(7, "unsafe client", JTI, EXPIRES_AT));
        assertThatIllegalArgumentException().isThrownBy(() -> new CurrentAccessTokenReference(7, "user", "bad", EXPIRES_AT));
        assertThatThrownBy(() -> new CurrentAccessTokenReference(7, "user", JTI, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void portRemainsApplicationNeutralAndComponentContextHasOneBean() {
        assertThat(CurrentAccessTokenAccessor.class.getDeclaredMethods()).hasSize(1);
        assertThat(CurrentAccessTokenAccessor.class.getDeclaredMethods()[0].getReturnType()).isEqualTo(Optional.class);
        assertThat(SecurityContextCurrentAccessTokenAccessor.class).hasAnnotation(Component.class);

        contextRunner.run(context -> {
            assertThat(context.getBeansOfType(CurrentAccessTokenAccessor.class)).hasSize(1);
            assertThat(context.getBeansOfType(SecurityContextCurrentAccessTokenAccessor.class)).hasSize(1);
        });
    }

    private void assertMalformed(Jwt jwt, String claim) {
        authenticate(jwt);
        assertThatThrownBy(accessor::currentAccessToken)
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Invalid authenticated JWT claim: " + claim);
    }

    private static Jwt jwt(Map<String, Object> claims, Instant expiresAt, String fallbackJti) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaims()).thenReturn(new HashMap<>(claims));
        when(jwt.getExpiresAt()).thenReturn(expiresAt);
        when(jwt.getId()).thenReturn(fallbackJti);
        return jwt;
    }

    private static void authenticate(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(SecurityContextCurrentAccessTokenAccessor.class)
    static class ComponentConfiguration {
    }
}
