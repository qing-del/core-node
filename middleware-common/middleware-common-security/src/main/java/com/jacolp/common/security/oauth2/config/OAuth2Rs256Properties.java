package com.jacolp.common.security.oauth2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * External configuration for the mandatory RS256 OAuth2 token capability.
 * Token lifetimes intentionally remain client-owned {@code token_settings} data.
 */
@Component
@ConfigurationProperties(prefix = "jacolp.oauth2.rs256")
public class OAuth2Rs256Properties {

    private String issuer = "core-node";
    private String audience = "core-node-api";
    private Resource privateKeyLocation;
    private Resource publicKeyLocation;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public Resource getPrivateKeyLocation() {
        return privateKeyLocation;
    }

    public void setPrivateKeyLocation(Resource privateKeyLocation) {
        this.privateKeyLocation = privateKeyLocation;
    }

    public Resource getPublicKeyLocation() {
        return publicKeyLocation;
    }

    public void setPublicKeyLocation(Resource publicKeyLocation) {
        this.publicKeyLocation = publicKeyLocation;
    }
}
