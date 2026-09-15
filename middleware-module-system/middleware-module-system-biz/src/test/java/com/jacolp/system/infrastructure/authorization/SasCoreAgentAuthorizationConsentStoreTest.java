package com.jacolp.system.infrastructure.authorization;

import com.jacolp.system.application.port.out.CoreAgentAuthorizationConsentStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SasCoreAgentAuthorizationConsentStoreTest {

    private static final String REGISTERED_CLIENT_ID = "e7cf5b30-8e43-4db2-bc53-000000000003";
    private static final String PRINCIPAL_NAME = "7";

    @Test
    void roundTripsOfficialConsentAuthoritiesAsSortedImmutableScopePatterns() {
        OAuth2AuthorizationConsentService sas = mock(OAuth2AuthorizationConsentService.class);
        SasCoreAgentAuthorizationConsentStore store = new SasCoreAgentAuthorizationConsentStore(sas);
        OAuth2AuthorizationConsent consent = consent(REGISTERED_CLIENT_ID, PRINCIPAL_NAME,
                new SimpleGrantedAuthority("SCOPE_note:write"), new SimpleGrantedAuthority("SCOPE_note:read"));
        when(sas.findById(REGISTERED_CLIENT_ID, PRINCIPAL_NAME)).thenReturn(consent);

        assertThat(store.findScopes(REGISTERED_CLIENT_ID, PRINCIPAL_NAME))
                .contains(linkedSet("note:read", "note:write"));
        assertThatThrownBy(() -> store.findScopes(REGISTERED_CLIENT_ID, PRINCIPAL_NAME).orElseThrow()
                .add("media:read")).isInstanceOf(UnsupportedOperationException.class);

        store.saveScopes(REGISTERED_CLIENT_ID, PRINCIPAL_NAME, List.of("note:write", "note:read"));
        org.mockito.ArgumentCaptor<OAuth2AuthorizationConsent> captor = org.mockito.ArgumentCaptor.forClass(
                OAuth2AuthorizationConsent.class);
        verify(sas).save(captor.capture());
        assertThat(captor.getValue().getRegisteredClientId()).isEqualTo(REGISTERED_CLIENT_ID);
        assertThat(captor.getValue().getPrincipalName()).isEqualTo(PRINCIPAL_NAME);
        assertThat(captor.getValue().getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("SCOPE_note:read", "SCOPE_note:write");
    }

    @Test
    void missingOfficialConsentIsEmptyButIdentityAndAuthorityPollutionFailClosed() {
        OAuth2AuthorizationConsentService sas = mock(OAuth2AuthorizationConsentService.class);
        SasCoreAgentAuthorizationConsentStore store = new SasCoreAgentAuthorizationConsentStore(sas);
        when(sas.findById(REGISTERED_CLIENT_ID, PRINCIPAL_NAME)).thenReturn(null);
        assertThat(store.findScopes(REGISTERED_CLIENT_ID, PRINCIPAL_NAME)).isEmpty();

        when(sas.findById(REGISTERED_CLIENT_ID, PRINCIPAL_NAME)).thenReturn(consent("other", PRINCIPAL_NAME,
                new SimpleGrantedAuthority("SCOPE_note:read")));
        assertThatIllegalStateException().isThrownBy(() -> store.findScopes(REGISTERED_CLIENT_ID, PRINCIPAL_NAME));

        when(sas.findById(REGISTERED_CLIENT_ID, PRINCIPAL_NAME)).thenReturn(consent(REGISTERED_CLIENT_ID,
                PRINCIPAL_NAME, new SimpleGrantedAuthority("ROLE_USER")));
        assertThatIllegalStateException().isThrownBy(() -> store.findScopes(REGISTERED_CLIENT_ID, PRINCIPAL_NAME));
    }

    @Test
    void rejectsDuplicateAndMalformedAuthoritiesAndInvalidSaveInput() {
        OAuth2AuthorizationConsentService sas = mock(OAuth2AuthorizationConsentService.class);
        SasCoreAgentAuthorizationConsentStore store = new SasCoreAgentAuthorizationConsentStore(sas);
        Set<GrantedAuthority> duplicateAuthorities = new LinkedHashSet<>();
        duplicateAuthorities.add(() -> "SCOPE_note:read");
        duplicateAuthorities.add(() -> "SCOPE_note:read");
        when(sas.findById(REGISTERED_CLIENT_ID, PRINCIPAL_NAME)).thenReturn(consent(REGISTERED_CLIENT_ID,
                PRINCIPAL_NAME, duplicateAuthorities));
        assertThatIllegalStateException().isThrownBy(() -> store.findScopes(REGISTERED_CLIENT_ID, PRINCIPAL_NAME));

        assertThatIllegalArgumentException().isThrownBy(() -> store.saveScopes(REGISTERED_CLIENT_ID, PRINCIPAL_NAME,
                List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> store.saveScopes(REGISTERED_CLIENT_ID, PRINCIPAL_NAME,
                null));
        assertThatIllegalArgumentException().isThrownBy(() -> store.saveScopes(REGISTERED_CLIENT_ID, PRINCIPAL_NAME,
                List.of("note:read", "note:read")));
        assertThatIllegalArgumentException().isThrownBy(() -> store.saveScopes(REGISTERED_CLIENT_ID, "007",
                List.of("note:read")));
        assertThatIllegalArgumentException().isThrownBy(() -> store.saveScopes(" ", PRINCIPAL_NAME,
                List.of("note:read")));
    }

    @Test
    void officialServiceFailuresPropagateWithoutASecondSerializationPath() {
        OAuth2AuthorizationConsentService sas = mock(OAuth2AuthorizationConsentService.class);
        SasCoreAgentAuthorizationConsentStore store = new SasCoreAgentAuthorizationConsentStore(sas);
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(sas.findById(REGISTERED_CLIENT_ID, PRINCIPAL_NAME)).thenThrow(failure);

        assertThatThrownBy(() -> store.findScopes(REGISTERED_CLIENT_ID, PRINCIPAL_NAME)).isSameAs(failure);
    }

    @Test
    void applicationPortDoesNotExposeSpringAuthorizationServerTypes() {
        for (Method method : CoreAgentAuthorizationConsentStore.class.getDeclaredMethods()) {
            assertThat(method.getReturnType().getName()).doesNotContain("oauth2.server.authorization");
            for (Class<?> parameterType : method.getParameterTypes()) {
                assertThat(parameterType.getName()).doesNotContain("oauth2.server.authorization");
            }
        }
    }

    @Test
    void configurationAndAdapterAreAlwaysPresentWhenTheirDependenciesExist() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(CoreAgentAuthorizationConsentConfiguration.class, SasCoreAgentAuthorizationConsentStore.class);
        runner.withBean(JdbcOperations.class, () -> mock(JdbcOperations.class))
                .withBean(RegisteredClientRepository.class, () -> mock(RegisteredClientRepository.class))
                .run(context -> {
                    assertThat(context.getBeansOfType(OAuth2AuthorizationConsentService.class)).hasSize(1);
                    assertThat(context.getBeansOfType(CoreAgentAuthorizationConsentStore.class)).hasSize(1);
                });
    }

    private static OAuth2AuthorizationConsent consent(String clientId, String principalName,
                                                       GrantedAuthority... authorities) {
        return consent(clientId, principalName, new LinkedHashSet<>(List.of(authorities)));
    }

    private static OAuth2AuthorizationConsent consent(String clientId, String principalName,
                                                       Collection<GrantedAuthority> authorities) {
        OAuth2AuthorizationConsent.Builder builder = OAuth2AuthorizationConsent.withId(clientId, principalName);
        authorities.forEach(builder::authority);
        return builder.build();
    }

    private static Set<String> linkedSet(String... scopes) {
        return new LinkedHashSet<>(List.of(scopes));
    }
}
