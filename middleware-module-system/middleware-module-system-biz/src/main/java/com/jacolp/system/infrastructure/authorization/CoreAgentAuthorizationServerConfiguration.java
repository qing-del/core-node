package com.jacolp.system.infrastructure.authorization;

import com.jacolp.system.web.authorization.CoreAgentAuthorizationCodeRequestAuthenticationProvider;
import com.jacolp.system.web.authorization.CoreAgentAuthorizationCodeTokenAuthenticationConverter;
import com.jacolp.system.web.authorization.CoreAgentAuthorizationCodeTokenAuthenticationProvider;
import com.jacolp.system.web.authorization.CoreAgentAuthorizationConsentAuthenticationProvider;
import com.jacolp.system.web.authorization.CoreAgentAuthorizationEndpointAuthenticationConverter;
import com.jacolp.system.web.authorization.CoreAgentAuthorizationEndpointAuthenticationDetailsSource;
import com.jacolp.system.web.authorization.CoreAgentTokenEndpointAuthenticationDetailsSource;
import com.jacolp.system.web.authorization.CoreAgentPublicClientAuthenticationConverter;
import com.jacolp.system.web.authorization.CoreAgentPublicClientAuthenticationProvider;
import com.jacolp.system.web.authorization.CoreAgentRefreshTokenAuthenticationConverter;
import com.jacolp.system.web.authorization.CoreAgentRefreshTokenAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

/** Phase 4 endpoint settings and wiring metadata; this class deliberately creates no filter chain. */
@Configuration(proxyBeanMethods = false)
public class CoreAgentAuthorizationServerConfiguration {

    static final String AUTHORIZATION_ENDPOINT = "/oauth2/authorize";
    static final String TOKEN_ENDPOINT = "/oauth/token";
    static final String CONSENT_PAGE = "/oauth/consent";

    /**
     * Keeps issuer unset so SAS derives it from a trusted request context when the browser chain is
     * introduced. The project JWT issuer remains independently configured by OAuth2Rs256Properties.
     */
    @Bean
    AuthorizationServerSettings coreAgentAuthorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .authorizationEndpoint(AUTHORIZATION_ENDPOINT)
                .tokenEndpoint(TOKEN_ENDPOINT)
                .build();
    }

    @Bean
    CoreAgentAuthorizationEndpointAuthenticationDetailsSource coreAgentAuthorizationEndpointAuthenticationDetailsSource() {
        return new CoreAgentAuthorizationEndpointAuthenticationDetailsSource();
    }

    @Bean
    CoreAgentTokenEndpointAuthenticationDetailsSource coreAgentTokenEndpointAuthenticationDetailsSource() {
        return new CoreAgentTokenEndpointAuthenticationDetailsSource();
    }

    @Bean
    CoreAgentAuthorizationServerConfigurerFactory coreAgentAuthorizationServerConfigurerFactory(
            ActiveRegisteredClientRepository registeredClientRepository,
            FailClosedOAuth2AuthorizationService authorizationService,
            OAuth2AuthorizationConsentService authorizationConsentService,
            AuthorizationServerSettings coreAgentAuthorizationServerSettings,
            CoreAgentPublicClientAuthenticationConverter publicClientAuthenticationConverter,
            CoreAgentPublicClientAuthenticationProvider publicClientAuthenticationProvider,
            CoreAgentAuthorizationEndpointAuthenticationDetailsSource authorizationEndpointAuthenticationDetailsSource,
            CoreAgentTokenEndpointAuthenticationDetailsSource tokenEndpointAuthenticationDetailsSource,
            CoreAgentAuthorizationEndpointAuthenticationConverter authorizationEndpointAuthenticationConverter,
            CoreAgentAuthorizationCodeRequestAuthenticationProvider authorizationCodeRequestAuthenticationProvider,
            CoreAgentAuthorizationConsentAuthenticationProvider authorizationConsentAuthenticationProvider,
            CoreAgentAuthorizationCodeTokenAuthenticationConverter authorizationCodeTokenAuthenticationConverter,
            CoreAgentRefreshTokenAuthenticationConverter refreshTokenAuthenticationConverter,
            CoreAgentAuthorizationCodeTokenAuthenticationProvider authorizationCodeTokenAuthenticationProvider,
            CoreAgentRefreshTokenAuthenticationProvider refreshTokenAuthenticationProvider) {
        return new CoreAgentAuthorizationServerConfigurerFactory(registeredClientRepository, authorizationService,
                authorizationConsentService, coreAgentAuthorizationServerSettings, publicClientAuthenticationConverter,
                publicClientAuthenticationProvider, authorizationEndpointAuthenticationDetailsSource,
                tokenEndpointAuthenticationDetailsSource, authorizationEndpointAuthenticationConverter,
                authorizationCodeRequestAuthenticationProvider, authorizationConsentAuthenticationProvider,
                authorizationCodeTokenAuthenticationConverter, refreshTokenAuthenticationConverter,
                authorizationCodeTokenAuthenticationProvider, refreshTokenAuthenticationProvider);
    }
}
