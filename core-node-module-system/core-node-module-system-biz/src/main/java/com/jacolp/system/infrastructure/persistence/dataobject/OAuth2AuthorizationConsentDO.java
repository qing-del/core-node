package com.jacolp.system.infrastructure.persistence.dataobject;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * OAuth2 authorization consent persisted in {@code oauth2_authorization_consent}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2AuthorizationConsentDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String registeredClientId;
    private String principalName;
    private String authorities;
}
