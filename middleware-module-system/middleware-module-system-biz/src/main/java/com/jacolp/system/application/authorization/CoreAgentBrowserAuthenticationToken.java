package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.CoreAgentBrowserPrincipal;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Objects;

/** Read-only authenticated Spring Security identity for one CORE AGENT browser session. */
public final class CoreAgentBrowserAuthenticationToken extends AbstractAuthenticationToken {

    private final CoreAgentBrowserPrincipal principal;

    private CoreAgentBrowserAuthenticationToken(CoreAgentBrowserPrincipal principal) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + principal.roleCode())));
        this.principal = Objects.requireNonNull(principal, "principal");
        super.setAuthenticated(true);
    }

    public static CoreAgentBrowserAuthenticationToken authenticated(CoreAgentBrowserPrincipal principal) {
        return new CoreAgentBrowserAuthenticationToken(Objects.requireNonNull(principal, "principal"));
    }

    @Override
    public CoreAgentBrowserPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public String getName() {
        return Long.toString(principal.userId());
    }

    @Override
    public void setAuthenticated(boolean authenticated) {
        if (authenticated) {
            throw new IllegalArgumentException("CORE AGENT browser authentication token is read-only");
        }
        super.setAuthenticated(false);
    }

    @Override
    public void eraseCredentials() {
        // The token never retains credentials.
    }

    @Override
    public String toString() {
        return "CoreAgentBrowserAuthenticationToken[principal=<redacted>, authorities=" + getAuthorities()
                + ", authenticated=" + isAuthenticated() + ']';
    }
}
