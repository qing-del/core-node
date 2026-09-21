package com.jacolp.system.application.port.out;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Application-neutral persistence boundary for the scopes a browser user has consented to grant
 * the CORE AGENT client. Implementations require a nonblank safe registered-client id, a positive
 * decimal user-id principal name, and nonempty distinct canonical permission scope patterns.
 */
public interface CoreAgentAuthorizationConsentStore {

    Optional<Set<String>> findScopes(String registeredClientId, String principalName);

    void saveScopes(String registeredClientId, String principalName, Collection<String> scopes);
}
