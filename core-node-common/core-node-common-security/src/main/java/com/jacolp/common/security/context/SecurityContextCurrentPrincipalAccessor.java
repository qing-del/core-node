package com.jacolp.common.security.context;

import com.jacolp.common.core.exception.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads legacy bridge principals and RS256 JWT principals without exposing a bearer token. */
public final class SecurityContextCurrentPrincipalAccessor implements CurrentPrincipalAccessor {
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Optional<CurrentPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return Optional.empty();
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) return Optional.of(fromJwt(jwt));
        if (principal instanceof SecurityPrincipal legacy) return Optional.of(fromLegacy(legacy, authentication));
        return Optional.empty();
    }

    private static CurrentPrincipal fromLegacy(SecurityPrincipal principal, Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && authority.startsWith(ROLE_PREFIX) && authority.length() > ROLE_PREFIX.length())
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();
        return new CurrentPrincipal(principal.id(), null, null, null, roles, List.of());
    }

    /**
     * Normalizes an already authenticated RS256 JWT without reading the thread-local security context.
     * Resource-server authorization managers use this so their decision is based on the supplied authentication.
     */
    public static CurrentPrincipal fromJwt(Jwt jwt) {
        Map<String, Object> claims = jwt.getClaims();
        long userId = positiveSubject(claims.get("sub"));
        String username = requiredString(claims, "username");
        String clientId = requiredString(claims, "client_id");
        String grantType = requiredString(claims, "grant_type");
        List<String> roles = requiredStringArray(claims, "roles");
        if (roles.size() != 1) throw invalidClaim("roles");
        List<String> scopes = requiredStringArray(claims, "scope");
        return new CurrentPrincipal(userId, username, clientId, grantType, roles, scopes);
    }

    private static long positiveSubject(Object subject) {
        if (!(subject instanceof String value) || value.isBlank() || !value.chars().allMatch(Character::isDigit)) {
            throw invalidClaim("sub");
        }
        try {
            long userId = Long.parseLong(value);
            if (userId <= 0) throw invalidClaim("sub");
            return userId;
        } catch (NumberFormatException exception) {
            throw invalidClaim("sub");
        }
    }

    private static String requiredString(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (!(value instanceof String string) || string.isBlank()) throw invalidClaim(name);
        return string;
    }

    private static List<String> requiredStringArray(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (!(value instanceof List<?> values) || values.stream().anyMatch(item -> !(item instanceof String string) || string.isBlank())) {
            throw invalidClaim(name);
        }
        return values.stream().map(String.class::cast).toList();
    }

    private static AuthenticationException invalidClaim(String name) {
        return new AuthenticationException("Invalid authenticated JWT claim: " + name);
    }
}
