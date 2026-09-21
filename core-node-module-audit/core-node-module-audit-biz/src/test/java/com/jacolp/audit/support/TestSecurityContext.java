package com.jacolp.audit.support;

import com.jacolp.common.security.context.SecurityIdentity;
import com.jacolp.common.security.context.SecurityPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

public final class TestSecurityContext {

    private TestSecurityContext() {
    }

    public static void authenticate(long userId, boolean administrative) {
        SecurityIdentity identity = administrative ? SecurityIdentity.ADMIN : SecurityIdentity.USER;
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new SecurityPrincipal(userId, identity), null,
                List.of(new SimpleGrantedAuthority(identity.authority()))));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
