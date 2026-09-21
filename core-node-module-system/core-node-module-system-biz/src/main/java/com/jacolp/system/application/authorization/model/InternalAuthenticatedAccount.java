package com.jacolp.system.application.authorization.model;

/**
 * Credential-free account identity cleared for an internal USER or ADMIN client login.
 */
public record InternalAuthenticatedAccount(
        Long userId,
        String username,
        String email,
        Long roleId,
        String roleCode,
        Integer rank) {

    public InternalAuthenticatedAccount {
        if (userId == null || userId <= 0 || roleId == null || roleId <= 0 || rank == null || rank <= 0) {
            throw new IllegalArgumentException("Internal authenticated account identifiers and rank must be positive");
        }
        requireText(username, "username");
        requireText(roleCode, "roleCode");
        if (email != null) {
            requireText(email, "email");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Internal authenticated account " + fieldName + " cannot be blank");
        }
    }
}
