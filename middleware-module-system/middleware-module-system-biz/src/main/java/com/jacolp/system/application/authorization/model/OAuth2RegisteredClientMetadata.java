package com.jacolp.system.application.authorization.model;

import java.time.LocalDateTime;

/**
 * Application-facing OAuth2 registered-client metadata.
 * Settings and collection values retain their persisted string representation.
 */
public record OAuth2RegisteredClientMetadata(
        String id,
        String clientId,
        LocalDateTime clientIdIssuedAt,
        String clientSecret,
        LocalDateTime clientSecretExpiresAt,
        String clientName,
        String clientAuthenticationMethods,
        String authorizationGrantTypes,
        String redirectUris,
        String postLogoutRedirectUris,
        String scopes,
        String clientSettings,
        String tokenSettings,
        String autoApprove,
        String status,
        String allowedIps) {
}
