package com.jacolp.common.security.oauth2.authorization;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Matches an issued wildcard scope against a required route scope without expanding a permission catalogue. */
public final class PermissionScopeMatcher {

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private PermissionScopeMatcher() {
    }

    /**
     * Returns whether at least one granted pattern covers the required pattern.
     * A specific grant never satisfies a broader wildcard requirement.
     */
    public static boolean grants(Collection<String> grantedScopes, String requiredScope) {
        Objects.requireNonNull(grantedScopes, "grantedScopes must not be null");
        ScopePattern required = ScopePattern.parse(requiredScope);
        for (String grantedScope : grantedScopes) {
            if (ScopePattern.parse(grantedScope).covers(required)) {
                return true;
            }
        }
        return false;
    }

    /** Returns whether every required route scope is covered by the issued scope collection. */
    public static boolean grantsAll(Collection<String> grantedScopes, Collection<String> requiredScopes) {
        Objects.requireNonNull(requiredScopes, "requiredScopes must not be null");
        return requiredScopes.stream().allMatch(requiredScope -> grants(grantedScopes, requiredScope));
    }

    private record ScopePattern(String resource, String action) {

        private ScopePattern {
            validateComponent(resource, "resource");
            validateComponent(action, "action");
        }

        static ScopePattern parse(String scope) {
            if (scope == null || !scope.equals(scope.trim())) {
                throw new IllegalArgumentException("Scope must be canonical resource:action");
            }
            int separator = scope.indexOf(':');
            if (separator <= 0 || separator != scope.lastIndexOf(':') || separator == scope.length() - 1) {
                throw new IllegalArgumentException("Scope must be canonical resource:action");
            }
            return new ScopePattern(scope.substring(0, separator), scope.substring(separator + 1));
        }

        boolean covers(ScopePattern required) {
            return meetComponent(resource, required.resource).filter(required.resource::equals).isPresent()
                    && meetComponent(action, required.action).filter(required.action::equals).isPresent();
        }

        private static Optional<String> meetComponent(String left, String right) {
            if (left.equals(right)) {
                return Optional.of(left);
            }
            if ("*".equals(left)) {
                return Optional.of(right);
            }
            if ("*".equals(right)) {
                return Optional.of(left);
            }
            return Optional.empty();
        }

        private static void validateComponent(String component, String name) {
            if (component == null || (!"*".equals(component) && !TOKEN.matcher(component).matches())) {
                throw new IllegalArgumentException("Scope " + name + " must be a token or *");
            }
        }
    }
}
