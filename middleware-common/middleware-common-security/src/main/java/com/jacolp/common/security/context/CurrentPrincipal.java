package com.jacolp.common.security.context;

import java.util.List;
import java.util.Objects;

/** Normalized current identity without bearer credentials or authorization-token claims. */
public record CurrentPrincipal(long userId, String username, String clientId, String grantType,
                               List<String> roles, List<String> scopes) {
    public CurrentPrincipal {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        roles = immutableStrings(roles, "roles");
        scopes = immutableStrings(scopes, "scopes");
    }

    public boolean isAdministrative() {
        return roles.stream().anyMatch(role -> "admin".equalsIgnoreCase(role) || "creator".equalsIgnoreCase(role));
    }

    private static List<String> immutableStrings(List<String> values, String name) {
        values = List.copyOf(Objects.requireNonNull(values, name + " must not be null"));
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(name + " must contain non-blank strings");
        }
        return values;
    }
}
