package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.CoreAgentBrowserPrincipal;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Spring Security adapter for the application-only CORE AGENT browser account authenticator. */
@Component
public final class CoreAgentBrowserAuthenticationProvider implements AuthenticationProvider {

    static final String BAD_CREDENTIALS_MESSAGE = "CORE AGENT browser credentials rejected";

    private final CoreAgentBrowserAccountAuthenticator accountAuthenticator;

    public CoreAgentBrowserAuthenticationProvider(CoreAgentBrowserAccountAuthenticator accountAuthenticator) {
        this.accountAuthenticator = Objects.requireNonNull(accountAuthenticator, "accountAuthenticator");
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        if (authentication == null || !supports(authentication.getClass())) {
            throw new AuthenticationServiceException("Unsupported CORE AGENT browser authentication token");
        }
        Object principal = authentication.getPrincipal();
        Object credentials = authentication.getCredentials();
        if (!(principal instanceof String username) || !(credentials instanceof String rawPassword)) {
            throw badCredentials();
        }
        try {
            CoreAgentBrowserPrincipal browserPrincipal = accountAuthenticator.authenticate(username, rawPassword);
            if (browserPrincipal == null) {
                throw new AuthenticationServiceException("CORE AGENT browser authenticator returned null");
            }
            return CoreAgentBrowserAuthenticationToken.authenticated(browserPrincipal);
        } catch (CoreAgentBrowserAuthenticationRejectedException exception) {
            throw badCredentials();
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication != null && UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static BadCredentialsException badCredentials() {
        return new BadCredentialsException(BAD_CREDENTIALS_MESSAGE);
    }
}
