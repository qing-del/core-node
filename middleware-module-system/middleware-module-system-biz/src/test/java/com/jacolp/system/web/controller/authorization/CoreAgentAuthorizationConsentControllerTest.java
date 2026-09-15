package com.jacolp.system.web.controller.authorization;

import com.jacolp.system.application.authorization.CoreAgentAuthorizationConsentService;
import com.jacolp.system.application.authorization.CoreAgentBrowserAuthenticationToken;
import com.jacolp.system.application.authorization.CoreAgentRegisteredClientPolicyResolver;
import com.jacolp.system.application.authorization.EffectiveRolePermissionResolver;
import com.jacolp.system.application.port.out.CoreAgentPendingAuthorizationStore;
import com.jacolp.system.web.authorization.HttpSessionCoreAgentPendingAuthorizationHandleStore;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationConsentDecision;
import com.jacolp.system.application.authorization.model.CoreAgentBrowserPrincipal;
import com.jacolp.system.application.authorization.model.CoreAgentConsentScopeOptions;
import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationState;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationPendingHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ConcurrentModel;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class CoreAgentAuthorizationConsentControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-11T10:00:00Z");
    private static final String REDIRECT_URI = "http://127.0.0.1:9090/oauth/callback";
    private static final String STATE = "client-state";
    private static final String HANDLE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ControllerOnlyConfiguration.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rendersMandatoryOptionalAndPreselectedScopesFromServerSideDecision() {
        Fixture fixture = fixture();
        authenticate(principal());
        retainHandle(fixture);
        when(fixture.pendingStore.find(HANDLE)).thenReturn(Optional.of(pending(fixture.session,
                List.of("note:read", "note:write"))));
        when(fixture.consentService.prepare(eq(7L), any(), any(), eq(List.of("note:read", "note:write"))))
                .thenReturn(decision(List.of("note:read", "note:write"), List.of("note:read"),
                        List.of("note:write"), List.of("note:write")));

        ConcurrentModel model = new ConcurrentModel();
        String view = fixture.controller.consent(parameters("core_agent", STATE, "note:write note:read"),
                servletRequest(fixture.session), model);

        assertThat(view).isEqualTo("oauth/consent");
        assertThat(model.asMap()).containsEntry("clientId", "core_agent").containsEntry("oauthState", STATE)
                .containsEntry("mandatoryScopes", List.of("note:read"))
                .containsEntry("optionalScopes", List.of("note:write"))
                .containsEntry("preselectedOptionalScopes", List.of("note:write"));
        verify(fixture.consentService).prepare(eq(7L), any(), any(), eq(List.of("note:read", "note:write")));
    }

    @Test
    void rendersAnApproveablePageWhenOnlyMandatoryScopesRemain() {
        Fixture fixture = fixture();
        authenticate(principal());
        retainHandle(fixture);
        when(fixture.pendingStore.find(HANDLE)).thenReturn(Optional.of(pending(fixture.session, null)));
        when(fixture.consentService.prepare(eq(7L), any(), any(), eq(null)))
                .thenReturn(decision(List.of("note:read"), List.of("note:read"), List.of(), List.of()));

        ConcurrentModel model = new ConcurrentModel();
        String view = fixture.controller.consent(parameters("core_agent", STATE, ""), servletRequest(fixture.session),
                model);

        assertThat(view).isEqualTo("oauth/consent");
        assertThat(model.asMap().get("mandatoryScopes")).isEqualTo(List.of("note:read"));
        assertThat(model.asMap().get("optionalScopes")).isEqualTo(List.of());
    }

    @Test
    void rejectsMissingDuplicateOrTamperedQueryParametersWithoutInvokingConsent() {
        Fixture fixture = fixture();
        authenticate(principal());
        retainHandle(fixture);

        MultiValueMap<String, String> duplicateClient = parameters("core_agent", STATE, "note:read");
        duplicateClient.add("client_id", "core_agent");
        assertBadRequest(() -> fixture.controller.consent(duplicateClient, servletRequest(fixture.session), new ConcurrentModel()));

        MultiValueMap<String, String> missingScope = parameters("core_agent", STATE, "note:read");
        missingScope.remove("scope");
        assertBadRequest(() -> fixture.controller.consent(missingScope, servletRequest(fixture.session), new ConcurrentModel()));

        MultiValueMap<String, String> extraParameter = parameters("core_agent", STATE, "note:read");
        extraParameter.add("redirect_uri", "http://attacker.example/callback");
        assertBadRequest(() -> fixture.controller.consent(extraParameter, servletRequest(fixture.session), new ConcurrentModel()));

        MultiValueMap<String, String> malformedScope = parameters("core_agent", STATE, "note:read  note:write");
        assertBadRequest(() -> fixture.controller.consent(malformedScope, servletRequest(fixture.session), new ConcurrentModel()));
        verify(fixture.consentService, never()).prepare(any(), any(), any(), any());
    }

    @Test
    void rejectsWrongSessionUserClientAndStateWithoutLeakingRedirectState() {
        Fixture client = fixture();
        authenticate(principal());
        retainHandle(client);
        assertBadRequest(() -> client.controller.consent(parameters("other", STATE, "note:read"),
                servletRequest(client.session), new ConcurrentModel()));

        Fixture state = fixture();
        authenticate(principal());
        retainHandle(state);
        when(state.pendingStore.find(HANDLE)).thenReturn(Optional.of(pending(state.session, List.of("note:read"))));
        assertBadRequest(() -> state.controller.consent(parameters("core_agent", "other-state", "note:read"),
                servletRequest(state.session), new ConcurrentModel()));

        Fixture user = fixture();
        authenticate(new CoreAgentBrowserPrincipal(8L, "bob", 2L, "USER", 2));
        retainHandle(user);
        when(user.pendingStore.find(HANDLE)).thenReturn(Optional.of(pending(user.session, List.of("note:read"))));
        assertBadRequest(() -> user.controller.consent(parameters("core_agent", STATE, "note:read"),
                servletRequest(user.session), new ConcurrentModel()));

        Fixture session = fixture();
        authenticate(principal());
        retainHandle(session);
        MockHttpSession otherSession = new MockHttpSession();
        when(session.pendingStore.find(HANDLE)).thenReturn(Optional.of(pending(otherSession, List.of("note:read"))));
        assertBadRequest(() -> session.controller.consent(parameters("core_agent", STATE, "note:read"),
                servletRequest(session.session), new ConcurrentModel()));
    }

    @Test
    void requiresAnAuthenticatedBrowserPrincipalAndConsistentRoleBeforeRendering() {
        Fixture unauthenticated = fixture();
        retainHandle(unauthenticated);
        assertBadRequest(() -> unauthenticated.controller.consent(parameters("core_agent", STATE, "note:read"),
                servletRequest(unauthenticated.session), new ConcurrentModel()));

        Fixture roleMismatch = fixture();
        authenticate(principal());
        retainHandle(roleMismatch);
        when(roleMismatch.pendingStore.find(HANDLE)).thenReturn(Optional.of(pending(roleMismatch.session,
                List.of("note:read"))));
        when(roleMismatch.roleResolver.resolve(2L)).thenReturn(new EffectiveRolePermissions(2L, "ADMIN", 2,
                List.of("note:read")));
        assertThatIllegalStateException().isThrownBy(() -> roleMismatch.controller.consent(
                parameters("core_agent", STATE, "note:read"), servletRequest(roleMismatch.session), new ConcurrentModel()));
        verify(roleMismatch.consentService, never()).prepare(any(), any(), any(), any());
    }

    @Test
    void mapsOnlyExactGetConsentPathAndIsConditionallyRegistered() throws Exception {
        GetMapping mapping = CoreAgentAuthorizationConsentController.class.getMethod("consent", MultiValueMap.class,
                jakarta.servlet.http.HttpServletRequest.class, org.springframework.ui.Model.class)
                .getAnnotation(GetMapping.class);
        assertThat(mapping.value()).containsExactly(CoreAgentAuthorizationConsentController.CONSENT_PATH);
        assertThat(CoreAgentAuthorizationConsentController.class.getMethod("consent", MultiValueMap.class,
                jakarta.servlet.http.HttpServletRequest.class, org.springframework.ui.Model.class)
                .getAnnotation(PostMapping.class)).isNull();

        Fixture fixture = fixture();
        authenticate(principal());
        retainHandle(fixture);
        when(fixture.pendingStore.find(HANDLE)).thenReturn(Optional.of(pending(fixture.session, null)));
        when(fixture.consentService.prepare(any(), any(), any(), eq(null)))
                .thenReturn(decision(List.of("note:read"), List.of("note:read"), List.of(), List.of()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(fixture.controller).build();
        mvc.perform(get(CoreAgentAuthorizationConsentController.CONSENT_PATH).session(fixture.session)
                        .param("client_id", "core_agent").param("state", STATE).param("scope", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("oauth/consent"));
        mvc.perform(post(CoreAgentAuthorizationConsentController.CONSENT_PATH))
                .andExpect(status().isMethodNotAllowed());

        runner.withUserConfiguration(DependencyConfiguration.class)
                .run(context -> assertThat(context.getBeansOfType(CoreAgentAuthorizationConsentController.class)).hasSize(1));
    }

    @Test
    void templateKeepsMandatoryScopesOutOfCheckboxesAndPostsOnlyTheSasConsentSurface() throws IOException {
        String template = readTemplate();
        String lower = template.toLowerCase(java.util.Locale.ROOT);
        int mandatoryStart = template.indexOf("class=\"mandatory\"");
        int formStart = template.indexOf("<form method=\"post\"");
        assertThat(mandatoryStart).isGreaterThanOrEqualTo(0);
        assertThat(formStart).isGreaterThan(mandatoryStart);
        assertThat(template.substring(mandatoryStart, formStart)).doesNotContain("checkbox", "<input");
        assertThat(template).contains("<form method=\"post\" action=\"/oauth2/authorize\"",
                "th:action=\"@{/oauth2/authorize}\"", "name=\"client_id\"", "name=\"state\"",
                "${_csrf", "name=\"scope\"", "name=\"consent_action\" value=\"approve\"",
                "name=\"consent_action\" value=\"deny\"", "preselectedOptionalScopes.contains(scope)");
        assertThat(lower).doesNotContain("password", "email", "raw code", "pending handle", "session id",
                "client_secret", "<script", "src=\"http", "href=\"http", "x-forwarded-for");
    }

    private static void authenticate(CoreAgentBrowserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(CoreAgentBrowserAuthenticationToken.authenticated(principal));
    }

    private static MockHttpServletRequest servletRequest(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", CoreAgentAuthorizationConsentController.CONSENT_PATH);
        request.setSession(session);
        return request;
    }

    private static MultiValueMap<String, String> parameters(String clientId, String state, String scope) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("client_id", clientId);
        parameters.add("state", state);
        parameters.add("scope", scope);
        return parameters;
    }

    private static void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static Fixture fixture() {
        CoreAgentRegisteredClientPolicyResolver policyResolver = mock(CoreAgentRegisteredClientPolicyResolver.class);
        when(policyResolver.resolve(CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID)).thenReturn(policy());
        EffectiveRolePermissionResolver roleResolver = mock(EffectiveRolePermissionResolver.class);
        when(roleResolver.resolve(2L)).thenReturn(role());
        CoreAgentAuthorizationConsentService consentService = mock(CoreAgentAuthorizationConsentService.class);
        HttpSessionCoreAgentPendingAuthorizationHandleStore handleStore =
                new HttpSessionCoreAgentPendingAuthorizationHandleStore();
        CoreAgentPendingAuthorizationStore pendingStore = mock(CoreAgentPendingAuthorizationStore.class);
        CoreAgentAuthorizationConsentController controller = new CoreAgentAuthorizationConsentController(policyResolver,
                roleResolver, consentService, handleStore, pendingStore);
        return new Fixture(controller, policyResolver, roleResolver, consentService, handleStore, pendingStore,
                new MockHttpSession());
    }

    private static void retainHandle(Fixture fixture) {
        fixture.handleStore.replace(fixture.session, new IssuedCoreAgentAuthorizationPendingHandle(HANDLE,
                Instant.now().plus(Duration.ofMinutes(10))));
    }

    private static CoreAgentPendingAuthorizationState pending(MockHttpSession session, List<String> requestedScopes) {
        return new CoreAgentPendingAuthorizationState("core_agent", REDIRECT_URI, requestedScopes, HANDLE, "S256",
                STATE, "127.0.0.1", 7L, session.getId(), NOW, NOW.plus(Duration.ofMinutes(10)));
    }

    private static CoreAgentAuthorizationConsentDecision decision(List<String> candidate, List<String> mandatory,
                                                                  List<String> optional, List<String> preselected) {
        return new CoreAgentAuthorizationConsentDecision(new CoreAgentConsentScopeOptions(candidate, mandatory, optional,
                preselected), true, List.of());
    }

    private static CoreAgentRegisteredClientPolicy policy() {
        return new CoreAgentRegisteredClientPolicy("registered-core-agent", "core_agent", REDIRECT_URI,
                Set.of("note:read", "note:write"), Set.of("note:read"), "127.0.0.1/32",
                Duration.ofHours(1), Duration.ofHours(24), Duration.ofMinutes(10));
    }

    private static EffectiveRolePermissions role() {
        return new EffectiveRolePermissions(2L, "USER", 2, List.of("note:read", "note:write"));
    }

    private static CoreAgentBrowserPrincipal principal() {
        return new CoreAgentBrowserPrincipal(7L, "alice", 2L, "USER", 2);
    }

    private static String readTemplate() throws IOException {
        try (InputStream stream = CoreAgentAuthorizationConsentControllerTest.class.getClassLoader()
                .getResourceAsStream("templates/oauth/consent.html")) {
            assertThat(stream).as("consent template classpath resource").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record Fixture(CoreAgentAuthorizationConsentController controller,
                           CoreAgentRegisteredClientPolicyResolver policyResolver,
                           EffectiveRolePermissionResolver roleResolver,
                           CoreAgentAuthorizationConsentService consentService,
                           HttpSessionCoreAgentPendingAuthorizationHandleStore handleStore,
                           CoreAgentPendingAuthorizationStore pendingStore,
                           MockHttpSession session) {
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentAuthorizationConsentController.class)
    static class ControllerOnlyConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class DependencyConfiguration {
        @Bean CoreAgentRegisteredClientPolicyResolver policyResolver() {
            return mock(CoreAgentRegisteredClientPolicyResolver.class);
        }

        @Bean EffectiveRolePermissionResolver roleResolver() {
            return mock(EffectiveRolePermissionResolver.class);
        }

        @Bean CoreAgentAuthorizationConsentService consentService() {
            return mock(CoreAgentAuthorizationConsentService.class);
        }

        @Bean HttpSessionCoreAgentPendingAuthorizationHandleStore pendingHandleStore() {
            return new HttpSessionCoreAgentPendingAuthorizationHandleStore();
        }

        @Bean CoreAgentPendingAuthorizationStore pendingAuthorizationStore() {
            return mock(CoreAgentPendingAuthorizationStore.class);
        }
    }
}
