package com.jacolp.system.application.authorization.model;

import java.time.Duration;
import java.util.Set;

/**
 * Application-neutral policy resolved for one USER or ADMIN internal login request.
 */
public record InternalRegisteredClientPolicy(
        String registeredClientId,
        String clientId,
        String grantType,
        Set<String> scopes,
        Set<String> autoApproveScopes,
        String allowedIps,
        Duration accessTokenTimeToLive,
        Duration refreshTokenTimeToLive) {

    public InternalRegisteredClientPolicy {
        scopes = Set.copyOf(scopes);
        autoApproveScopes = Set.copyOf(autoApproveScopes);
    }
}
