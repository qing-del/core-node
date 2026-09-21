package com.jacolp.system.infrastructure.persistence.dataobject;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * OAuth2 client metadata persisted in {@code oauth2_registered_client}.
 * Settings and collection fields remain in their stored string form.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2RegisteredClientDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String clientId;
    private LocalDateTime clientIdIssuedAt;
    private String clientSecret;
    private LocalDateTime clientSecretExpiresAt;
    private String clientName;
    private String clientAuthenticationMethods;
    private String authorizationGrantTypes;
    private String redirectUris;
    private String postLogoutRedirectUris;
    private String scopes;
    private String clientSettings;
    private String tokenSettings;
    private String autoApprove;
    private String status;
    private String allowedIps;
}
