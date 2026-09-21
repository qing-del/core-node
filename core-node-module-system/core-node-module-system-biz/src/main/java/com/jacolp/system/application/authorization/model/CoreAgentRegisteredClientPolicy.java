package com.jacolp.system.application.authorization.model;

import java.time.Duration;
import java.util.Set;

/**
 * Immutable, application-neutral authorization policy for the fixed public CORE AGENT client.
 *
 * <p>This object intentionally exposes no Spring Authorization Server or persistence types so
 * authorize, token, and logout use the same verified client configuration boundary.</p>
 */
public record CoreAgentRegisteredClientPolicy(
        String registeredClientId,
        String clientId,
        String redirectUri,
        Set<String> scopes,
        Set<String> autoApproveScopes,
        String allowedIps,
        Duration accessTokenTimeToLive,
        Duration refreshTokenTimeToLive,
        Duration authorizationCodeTimeToLive) {

    public CoreAgentRegisteredClientPolicy {
        scopes = Set.copyOf(scopes);
        autoApproveScopes = Set.copyOf(autoApproveScopes);
    }
}
