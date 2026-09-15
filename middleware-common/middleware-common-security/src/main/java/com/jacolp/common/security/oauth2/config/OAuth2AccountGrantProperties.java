package com.jacolp.common.security.oauth2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * Account-wide default grants. Account rows contain only explicit additions;
 * the mandatory default set remains external configuration.
 */
@Component
@ConfigurationProperties(prefix = "jacolp.oauth2.account")
public class OAuth2AccountGrantProperties {

    private List<String> defaultGrantTypes = AccountGrantTypeResolver.requiredDefaultGrantTypes();

    public List<String> getDefaultGrantTypes() {
        return defaultGrantTypes;
    }

    /**
     * Spring Boot binds both indexed YAML lists and comma-separated property values here.
     */
    public void setDefaultGrantTypes(List<String> defaultGrantTypes) {
        this.defaultGrantTypes = AccountGrantTypeResolver.normalizeDefaultGrantTypes(defaultGrantTypes)
                .stream().toList();
    }

    @PostConstruct
    void validateDefaults() {
        defaultGrantTypes = AccountGrantTypeResolver.normalizeDefaultGrantTypes(defaultGrantTypes)
                .stream().toList();
    }
}
