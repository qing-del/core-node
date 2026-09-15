package com.jacolp.system.infrastructure.authorization;

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Component;

/**
 * Explicitly rejects Spring Authorization Server authorization persistence.
 *
 * <p>CORE AGENT authorization codes live exclusively in the Phase 4 Redis code store. Returning
 * an apparent cache miss here could let an SAS default provider continue down an unintended
 * fallback path, so every operation fails closed and deliberately ignores every supplied value.</p>
 */
@Component
public final class FailClosedOAuth2AuthorizationService implements OAuth2AuthorizationService {

    static final String FAILURE_MESSAGE = "SAS OAuth2Authorization persistence is disabled";

    @Override
    public void save(OAuth2Authorization authorization) {
        throw rejected();
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        throw rejected();
    }

    @Override
    public OAuth2Authorization findById(String id) {
        throw rejected();
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        throw rejected();
    }

    @Override
    public String toString() {
        return "FailClosedOAuth2AuthorizationService[persistence=<disabled>]";
    }

    private static IllegalStateException rejected() {
        return new IllegalStateException(FAILURE_MESSAGE);
    }
}
