package com.jacolp.common.security.oauth2.jwt;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fail-closed structural validation for CORE NODE's own RS256 access-token claims.
 *
 * <p>Signature, issuer, audience, and revocation validation are intentionally separate concerns. This validator
 * rejects tokens that are structurally valid JWTs but cannot be one of the three fixed first-party client grants.</p>
 */
public final class CoreNodeAccessTokenClaimsValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            "invalid_token", "JWT access token claims are invalid", null);
    private static final Pattern POSITIVE_SUBJECT = Pattern.compile("[0-9]+");
    private static final Pattern SCOPE_COMPONENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final Set<String> USER_GRANTS = Set.of("password", "email-code", "refresh_token");
    private static final Set<String> USER_CLIENT_ROLES = Set.of("USER", "ADMIN", "CREATOR");
    private static final Set<String> ADMIN_GRANTS = Set.of("password", "email-code", "refresh_token");
    private static final Set<String> CORE_AGENT_GRANTS = Set.of("authorization_code", "refresh_token");
    private static final Set<String> CORE_AGENT_ROLES = Set.of("USER", "ADMIN", "CREATOR");

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (jwt == null || !hasPositiveSubject(jwt.getSubject())) {
            return invalid();
        }
        Map<String, Object> claims = jwt.getClaims();
        if (claims == null || !hasText(claims.get("username"))) {
            return invalid();
        }
        String clientId = stringClaim(claims.get("client_id"));
        String grantType = stringClaim(claims.get("grant_type"));
        String role = singleRole(claims.get("roles"));
        if (!isAllowedClientGrantRole(clientId, grantType, role) || !isCanonicalScopes(claims.get("scope"))) {
            return invalid();
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static boolean hasPositiveSubject(String subject) {
        if (subject == null || !POSITIVE_SUBJECT.matcher(subject).matches()) {
            return false;
        }
        try {
            return Long.parseLong(subject) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean isAllowedClientGrantRole(String clientId, String grantType, String role) {
        if (clientId == null || grantType == null || role == null) {
            return false;
        }
        return switch (clientId) {
            case "user" -> USER_GRANTS.contains(grantType) && USER_CLIENT_ROLES.contains(role);
            case "admin" -> ADMIN_GRANTS.contains(grantType) && ("ADMIN".equals(role) || "CREATOR".equals(role));
            case "core_agent" -> CORE_AGENT_GRANTS.contains(grantType) && CORE_AGENT_ROLES.contains(role);
            default -> false;
        };
    }

    private static String singleRole(Object value) {
        if (!(value instanceof List<?> roles) || roles.size() != 1 || !(roles.getFirst() instanceof String role)
                || !hasText(role) || !role.equals(role.trim())) {
            return null;
        }
        return role;
    }

    private static boolean isCanonicalScopes(Object value) {
        if (!(value instanceof List<?> scopes)) {
            return false;
        }
        Set<String> canonical = new LinkedHashSet<>();
        for (Object scope : scopes) {
            if (!(scope instanceof String text) || !isCanonicalScope(text) || !canonical.add(text)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCanonicalScope(String scope) {
        if (!hasText(scope) || !scope.equals(scope.trim())) {
            return false;
        }
        int separator = scope.indexOf(':');
        if (separator <= 0 || separator != scope.lastIndexOf(':') || separator == scope.length() - 1) {
            return false;
        }
        return isScopeComponent(scope.substring(0, separator)) && isScopeComponent(scope.substring(separator + 1));
    }

    private static boolean isScopeComponent(String component) {
        return "*".equals(component) || SCOPE_COMPONENT.matcher(component).matches();
    }

    private static String stringClaim(Object value) {
        return value instanceof String string && hasText(string) && string.equals(string.trim()) ? string : null;
    }

    private static boolean hasText(Object value) {
        return value instanceof String string && !string.isBlank();
    }

    private static OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
    }
}
