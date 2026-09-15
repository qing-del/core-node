package com.jacolp.system.web.controller.authorization;

import com.jacolp.system.application.port.out.CoreAgentPendingAuthorizationStore;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationConsentService;
import com.jacolp.system.application.authorization.CoreAgentBrowserAuthenticationToken;
import com.jacolp.system.application.authorization.CoreAgentRegisteredClientPolicyResolver;
import com.jacolp.system.application.authorization.EffectiveRolePermissionResolver;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationConsentDecision;
import com.jacolp.system.application.authorization.model.CoreAgentBrowserPrincipal;
import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationState;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.PermissionScopePattern;
import com.jacolp.system.web.authorization.HttpSessionCoreAgentPendingAuthorizationHandleStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Renders the fixed CORE AGENT consent page from server-side pending authorization state.
 *
 * <p>The GET query is treated solely as an SAS redirect shape check. Scope choices, client
 * policy, and the trusted redirect URI are always recovered and recomputed server-side; this
 * controller never reads or writes a raw authorization code.</p>
 */
@Controller
public final class CoreAgentAuthorizationConsentController {

    static final String CONSENT_PATH = "/oauth/consent";
    private static final Set<String> SAS_CONSENT_QUERY_PARAMETERS = Set.of("client_id", "state", "scope");

    private final CoreAgentRegisteredClientPolicyResolver policyResolver;
    private final EffectiveRolePermissionResolver effectiveRolePermissionResolver;
    private final CoreAgentAuthorizationConsentService authorizationConsentService;
    private final HttpSessionCoreAgentPendingAuthorizationHandleStore pendingHandleStore;
    private final CoreAgentPendingAuthorizationStore pendingAuthorizationStore;

    public CoreAgentAuthorizationConsentController(
            CoreAgentRegisteredClientPolicyResolver policyResolver,
            EffectiveRolePermissionResolver effectiveRolePermissionResolver,
            CoreAgentAuthorizationConsentService authorizationConsentService,
            HttpSessionCoreAgentPendingAuthorizationHandleStore pendingHandleStore,
            CoreAgentPendingAuthorizationStore pendingAuthorizationStore) {
        this.policyResolver = Objects.requireNonNull(policyResolver, "policyResolver");
        this.effectiveRolePermissionResolver = Objects.requireNonNull(effectiveRolePermissionResolver,
                "effectiveRolePermissionResolver");
        this.authorizationConsentService = Objects.requireNonNull(authorizationConsentService,
                "authorizationConsentService");
        this.pendingHandleStore = Objects.requireNonNull(pendingHandleStore, "pendingHandleStore");
        this.pendingAuthorizationStore = Objects.requireNonNull(pendingAuthorizationStore, "pendingAuthorizationStore");
    }

    /** The form POST deliberately belongs to SAS {@code /oauth2/authorize}, not this controller. */
    @GetMapping(CONSENT_PATH)
    public String consent(@RequestParam MultiValueMap<String, String> parameters,
                          HttpServletRequest request,
                          Model model) {
        Query query = query(parameters);
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw badRequest();
        }
        CoreAgentBrowserAuthenticationToken browserAuthentication = browserAuthentication();
        CoreAgentBrowserPrincipal principal = browserAuthentication.getPrincipal();
        String rawPendingHandle = pendingHandleStore.find(session).orElseThrow(
                CoreAgentAuthorizationConsentController::badRequest);
        CoreAgentPendingAuthorizationState pending = pending(rawPendingHandle);
        CoreAgentRegisteredClientPolicy policy = requiredPolicy();
        if (!matchesTrustedPending(query, session, principal, policy, pending)) {
            throw badRequest();
        }
        EffectiveRolePermissions effectiveRole = effectiveRolePermissionResolver.resolve(principal.roleId());
        verifyRoleIdentity(principal, effectiveRole);
        if (!query.scopes().equals(expectedSasScopeShape(pending.requestedScopes()))) {
            throw badRequest();
        }

        CoreAgentAuthorizationConsentDecision decision = authorizationConsentService.prepare(principal.userId(),
                effectiveRole, policy, pending.requestedScopes());
        if (decision == null || decision.options() == null) {
            throw new IllegalStateException("CORE AGENT consent preparation returned invalid data");
        }
        model.addAttribute("clientId", policy.clientId());
        model.addAttribute("oauthState", pending.oauthState());
        model.addAttribute("mandatoryScopes", decision.options().mandatoryScopes());
        model.addAttribute("optionalScopes", decision.options().optionalScopes());
        model.addAttribute("preselectedOptionalScopes", decision.options().preselectedOptionalScopes());
        return "oauth/consent";
    }

    private static Query query(MultiValueMap<String, String> parameters) {
        if (parameters == null || !parameters.keySet().equals(SAS_CONSENT_QUERY_PARAMETERS)) {
            throw badRequest();
        }
        String clientId = exactlyOne(parameters, "client_id");
        String state = exactlyOne(parameters, "state");
        String scope = exactlyOne(parameters, "scope");
        if (!CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID.equals(clientId)) {
            throw badRequest();
        }
        return new Query(clientId, state, scopes(scope));
    }

    private static String exactlyOne(MultiValueMap<String, String> parameters, String name) {
        List<String> values = parameters.get(name);
        if (values == null || values.size() != 1 || values.getFirst() == null) {
            throw badRequest();
        }
        return values.getFirst();
    }

    /** Parses SAS's one space-delimited {@code scope} query parameter without trusting it. */
    private static List<String> scopes(String rawScope) {
        if (rawScope.isEmpty()) {
            return List.of();
        }
        String[] parts = rawScope.split(" ", -1);
        LinkedHashSet<String> canonical = new LinkedHashSet<>();
        for (String part : parts) {
            if (part.isEmpty() || !part.equals(part.trim())) {
                throw badRequest();
            }
            try {
                String scope = PermissionScopePattern.parse(part).asScope();
                if (!part.equals(scope) || !canonical.add(scope)) {
                    throw badRequest();
                }
            } catch (IllegalArgumentException exception) {
                throw badRequest();
            }
        }
        List<String> sorted = new ArrayList<>(canonical);
        sorted.sort(String::compareTo);
        return List.copyOf(sorted);
    }

    private CoreAgentPendingAuthorizationState pending(String rawPendingHandle) {
        Optional<CoreAgentPendingAuthorizationState> pendingOptional = pendingAuthorizationStore.find(rawPendingHandle);
        if (pendingOptional == null) {
            throw new IllegalStateException("CORE AGENT pending authorization lookup returned null");
        }
        return pendingOptional.orElseThrow(CoreAgentAuthorizationConsentController::badRequest);
    }

    private CoreAgentRegisteredClientPolicy requiredPolicy() {
        CoreAgentRegisteredClientPolicy policy = policyResolver.resolve(
                CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID);
        if (policy == null || !CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID.equals(policy.clientId())
                || policy.registeredClientId() == null || policy.registeredClientId().isBlank()) {
            throw new IllegalStateException("CORE AGENT registered client policy is invalid");
        }
        return policy;
    }

    private static CoreAgentBrowserAuthenticationToken browserAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof CoreAgentBrowserAuthenticationToken browserAuthentication)
                || !browserAuthentication.isAuthenticated() || browserAuthentication.getCredentials() != null
                || browserAuthentication.getPrincipal() == null
                || !Long.toString(browserAuthentication.getPrincipal().userId()).equals(browserAuthentication.getName())) {
            throw badRequest();
        }
        return browserAuthentication;
    }

    private static boolean matchesTrustedPending(Query query,
                                                 HttpSession session,
                                                 CoreAgentBrowserPrincipal principal,
                                                 CoreAgentRegisteredClientPolicy policy,
                                                 CoreAgentPendingAuthorizationState pending) {
        return pending != null
                && pending.clientId().equals(policy.clientId())
                && pending.redirectUri().equals(policy.redirectUri())
                && pending.clientId().equals(query.clientId())
                && pending.oauthState().equals(query.state())
                && pending.authenticatedUserId() == principal.userId()
                && pending.sessionId().equals(session.getId());
    }

    private static void verifyRoleIdentity(CoreAgentBrowserPrincipal principal,
                                           EffectiveRolePermissions effectiveRole) {
        if (effectiveRole == null || !principal.roleId().equals(effectiveRole.roleId())
                || !principal.roleCode().equals(effectiveRole.roleCode())
                || !principal.rank().equals(effectiveRole.roleRank())) {
            throw new IllegalStateException("CORE AGENT browser role identity is inconsistent");
        }
    }

    private static List<String> expectedSasScopeShape(List<String> requestedScopes) {
        return requestedScopes == null ? List.of() : requestedScopes;
    }

    private static ResponseStatusException badRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }

    private record Query(String clientId, String state, List<String> scopes) {
        private Query {
            scopes = List.copyOf(scopes);
        }

        @Override
        public String toString() {
            return "CoreAgentConsentQuery[clientId=" + clientId + ", state=<redacted>, scopes=<redacted>]";
        }
    }
}
