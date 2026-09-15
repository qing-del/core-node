package com.jacolp.system.application.authorization.model;

/**
 * Read-only account metadata needed by authorization flows. Email may be absent for legacy password accounts;
 * email-code callers must separately require a non-blank email address.
 */
public record AuthorizationAccount(
        Long userId,
        String username,
        String passwordHash,
        String email,
        Long roleId,
        String extraGrantTypes,
        Integer status) {

    public AuthorizationAccount {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Authorization account userId must be positive");
        }
        if (roleId == null || roleId <= 0) {
            throw new IllegalArgumentException("Authorization account roleId must be positive");
        }
        requireText(username, "username");
        requireText(passwordHash, "passwordHash");
        if (email != null) {
            requireText(email, "email");
        }
        if (extraGrantTypes == null) {
            throw new IllegalArgumentException("Authorization account extraGrantTypes cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("Authorization account status cannot be null");
        }
    }

    @Override
    public String toString() {
        return "AuthorizationAccount[userId=" + userId + ", username=" + username + ", email=" + email
                + ", roleId=" + roleId + ", extraGrantTypes=" + extraGrantTypes + ", status=" + status + ']';
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Authorization account " + fieldName + " cannot be blank");
        }
    }
}
