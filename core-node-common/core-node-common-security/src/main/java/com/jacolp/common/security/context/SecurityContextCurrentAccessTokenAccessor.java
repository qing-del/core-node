package com.jacolp.common.security.context;

import com.jacolp.common.core.exception.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Extracts a validated, non-secret reference from a JWT Spring Security principal. */
@Component
public final class SecurityContextCurrentAccessTokenAccessor implements CurrentAccessTokenAccessor {
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final Pattern JTI = Pattern.compile("[A-Za-z0-9_-]{22}");
    private static final Pattern POSITIVE_SUBJECT = Pattern.compile("[0-9]+");

    @Override
    public Optional<CurrentAccessTokenReference> currentAccessToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        return Optional.of(fromJwt(jwt));
    }

    private static CurrentAccessTokenReference fromJwt(Jwt jwt) {
        Map<String, Object> claims = jwt.getClaims();
        long userId = positiveSubject(claims.get("sub"));
        String clientId = safeClientId(claims.get("client_id"));
        String jti = jti(claims, jwt);
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null) {
            throw invalidClaim("exp");
        }
        return new CurrentAccessTokenReference(userId, clientId, jti, expiresAt);
    }

    private static long positiveSubject(Object value) {
        if (!(value instanceof String subject) || !POSITIVE_SUBJECT.matcher(subject).matches()) {
            throw invalidClaim("sub");
        }
        try {
            long userId = Long.parseLong(subject);
            if (userId <= 0) {
                throw invalidClaim("sub");
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw invalidClaim("sub");
        }
    }

    private static String safeClientId(Object value) {
        if (!(value instanceof String clientId) || !CLIENT_ID.matcher(clientId).matches()) {
            throw invalidClaim("client_id");
        }
        return clientId;
    }

    private static String jti(Map<String, Object> claims, Jwt jwt) {
        String value;
        if (claims.containsKey("jti")) {
            Object claim = claims.get("jti");
            if (!(claim instanceof String string)) {
                throw invalidClaim("jti");
            }
            value = string;
        } else {
            value = jwt.getId();
        }
        if (value == null || !JTI.matcher(value).matches()) {
            throw invalidClaim("jti");
        }
        return value;
    }

    private static AuthenticationException invalidClaim(String name) {
        return new AuthenticationException("Invalid authenticated JWT claim: " + name);
    }
}
