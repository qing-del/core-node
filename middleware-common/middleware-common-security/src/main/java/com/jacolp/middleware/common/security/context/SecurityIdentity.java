package com.jacolp.middleware.common.security.context;

/** Authenticated identities mirrored into Spring Security during the MVC interceptor transition. */
public enum SecurityIdentity {
    ADMIN("ROLE_ADMIN"),
    USER("ROLE_USER"),
    ACTIVATION("ROLE_ACTIVATION");

    private final String authority;

    SecurityIdentity(String authority) {
        this.authority = authority;
    }

    public String authority() {
        return authority;
    }
}
