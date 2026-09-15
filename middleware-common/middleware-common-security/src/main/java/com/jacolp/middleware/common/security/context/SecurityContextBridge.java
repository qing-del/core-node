package com.jacolp.middleware.common.security.context;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/** Mirrors successfully authenticated legacy MVC requests into Spring Security's context. */
public final class SecurityContextBridge {

    private SecurityContextBridge() {
    }

    public static void authenticate(Long id, SecurityIdentity identity) {
        SecurityPrincipal principal = new SecurityPrincipal(id, identity);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(identity.authority())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }

    public static Long currentIdOrNull() {
        SecurityPrincipal principal = principalOrNull();
        return principal == null ? null : principal.id();
    }

    /** Returns null when no compatible holder identity exists, so callers can use their legacy fallback. */
    public static Boolean isAdminOrNull() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SecurityPrincipal)) {
            return null;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> SecurityIdentity.ADMIN.authority().equals(authority.getAuthority()));
    }

    private static SecurityPrincipal principalOrNull() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof SecurityPrincipal principal ? principal : null;
    }
}
