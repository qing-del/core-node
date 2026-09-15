package com.jacolp.system.application.authorization.model;

import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;

import java.util.List;

/**
 * Verified authorization-code exchange input for the later token issuance provider.
 *
 * <p>The provider must re-evaluate cached consent scopes against current role and client permissions
 * before issuing a token; this object intentionally preserves the cache snapshot only.</p>
 */
public record VerifiedCoreAgentAuthorizationCode(
        String registeredClientId,
        String clientId,
        Long userId,
        String username,
        Long roleId,
        List<String> consentScopes,
        String grantType,
        boolean socketAddressChanged) {

    public VerifiedCoreAgentAuthorizationCode {
        if (registeredClientId == null || registeredClientId.isBlank() || clientId == null || clientId.isBlank()
                || userId == null || userId <= 0 || username == null || username.isBlank() || roleId == null || roleId <= 0
                || consentScopes == null || consentScopes.isEmpty()
                || !AccountGrantTypeResolver.AUTHORIZATION_CODE.equals(grantType)) {
            throw new IllegalArgumentException("Invalid verified CORE AGENT authorization code");
        }
        consentScopes = List.copyOf(consentScopes);
    }
}
