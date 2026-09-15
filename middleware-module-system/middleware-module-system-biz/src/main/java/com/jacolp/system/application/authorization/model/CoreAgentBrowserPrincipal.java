package com.jacolp.system.application.authorization.model;

import java.util.Set;

/** Immutable authenticated identity kept by the CORE AGENT browser authorization flow. */
public record CoreAgentBrowserPrincipal(
        Long userId,
        String username,
        Long roleId,
        String roleCode,
        Integer rank) {

    private static final Set<String> SUPPORTED_ROLE_CODES = Set.of("CREATOR", "ADMIN", "USER");

    public CoreAgentBrowserPrincipal {
        if (userId == null || userId <= 0 || roleId == null || roleId <= 0 || rank == null || rank <= 0) {
            throw invalid("userId, roleId, and rank must be positive");
        }
        if (username == null || username.isBlank() || roleCode == null || !SUPPORTED_ROLE_CODES.contains(roleCode)) {
            throw invalid("username and roleCode are invalid");
        }
    }

    @Override
    public String toString() {
        return "CoreAgentBrowserPrincipal[userId=" + userId + ", username=<redacted>, roleId=" + roleId
                + ", roleCode=" + roleCode + ", rank=" + rank + ']';
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid CORE AGENT browser principal: " + message);
    }
}
