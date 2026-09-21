package com.jacolp.system.application.authorization.model;

/**
 * Security-relevant account fields captured when a CORE AGENT authorization code is issued.
 *
 * <p>The later exchange compares a current snapshot with this value and invalidates stale codes
 * after a change. Sensitive values are intentionally never included in {@link #toString()}.</p>
 */
public record CoreAgentAuthorizationAccountSnapshot(
        Long userId,
        String username,
        Long roleId,
        String passwordHash,
        String email,
        String extraGrantTypes,
        Integer status) {

    public CoreAgentAuthorizationAccountSnapshot {
        if (userId == null || userId <= 0) {
            throw invalid("userId must be positive");
        }
        if (roleId == null || roleId <= 0) {
            throw invalid("roleId must be positive");
        }
        requireText(username, "username");
        requireText(passwordHash, "passwordHash");
        if (email != null) {
            requireText(email, "email");
        }
        if (extraGrantTypes == null || status == null) {
            throw invalid("extraGrantTypes and status are required");
        }
    }

    @Override
    public String toString() {
        return "CoreAgentAuthorizationAccountSnapshot[userId=" + userId + ", username=" + username
                + ", roleId=" + roleId + ", passwordHash=<redacted>, email=<redacted>, extraGrantTypes=<redacted>"
                + ", status=" + status + ']';
    }

    private static void requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw invalid(name + " cannot be blank");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid CORE AGENT account snapshot: " + message);
    }
}
