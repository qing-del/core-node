package com.jacolp.system.web.authorization;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime policy for the CORE AGENT browser login form. */
@ConfigurationProperties(prefix = "jacolp.oauth2.browser-login")
public class CoreAgentBrowserLoginProperties {

    private boolean csrfEnabled = false;

    public boolean isCsrfEnabled() {
        return csrfEnabled;
    }

    public void setCsrfEnabled(boolean csrfEnabled) {
        this.csrfEnabled = csrfEnabled;
    }
}
