package com.jacolp.system.web.authorization;

import com.jacolp.system.application.port.out.CoreAgentPendingAuthorizationStore;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationCodeIssueRejectedException;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationCodeIssueService;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationConsentService;
import com.jacolp.system.application.authorization.CoreAgentBrowserAuthenticationToken;
import com.jacolp.system.application.authorization.CoreAgentRegisteredClientPolicyResolver;
import com.jacolp.system.application.authorization.EffectiveRolePermissionResolver;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationCodeIssueRequest;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationConsentDecision;
import com.jacolp.system.application.authorization.model.CoreAgentBrowserPrincipal;
import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationConversionRequest;
import com.jacolp.system.application.authorization.model.CoreAgentPreparedPendingAuthorization;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Owns only the first, browser authorization-code request for the fixed CORE AGENT client.
 *
 * <p>The provider deliberately returns Spring Authorization Server's standard success and
 * consent-required tokens, so {@code OAuth2AuthorizationEndpointFilter} retains its protocol
 * redirects. It never creates SAS {@code OAuth2Authorization} state: the pending consent request
 * is held only in Redis. Browser {@code HttpSession} retains at most its opaque pending handle;
 * an issued raw code is held only by {@link CoreAgentAuthorizationCodeIssueService}'s Redis-backed
 * store.</p>
 */
@Component
public final class CoreAgentAuthorizationCodeRequestAuthenticationProvider implements AuthenticationProvider {

    static final String INVALID_REQUEST_DESCRIPTION = "Invalid CORE AGENT authorization request";
    static final String ACCESS_DENIED_DESCRIPTION = "CORE AGENT authorization denied";

    private final CoreAgentRegisteredClientPolicyResolver policyResolver;
    private final EffectiveRolePermissionResolver effectiveRolePermissionResolver;
    private final CoreAgentAuthorizationConsentService authorizationConsentService;
    private final CoreAgentAuthorizationCodeIssueService authorizationCodeIssueService;
    private final HttpSessionCoreAgentPendingAuthorizationHandleStore pendingHandleStore;
    private final CoreAgentPendingAuthorizationStore pendingAuthorizationStore;

    @Autowired
    public CoreAgentAuthorizationCodeRequestAuthenticationProvider(
            CoreAgentRegisteredClientPolicyResolver policyResolver,
            EffectiveRolePermissionResolver effectiveRolePermissionResolver,
            CoreAgentAuthorizationConsentService authorizationConsentService,
            CoreAgentAuthorizationCodeIssueService authorizationCodeIssueService,
            HttpSessionCoreAgentPendingAuthorizationHandleStore pendingHandleStore,
            CoreAgentPendingAuthorizationStore pendingAuthorizationStore) {
        this.policyResolver = Objects.requireNonNull(policyResolver, "policyResolver");
        this.effectiveRolePermissionResolver = Objects.requireNonNull(effectiveRolePermissionResolver,
                "effectiveRolePermissionResolver");
        this.authorizationConsentService = Objects.requireNonNull(authorizationConsentService,
                "authorizationConsentService");
        this.authorizationCodeIssueService = Objects.requireNonNull(authorizationCodeIssueService,
                "authorizationCodeIssueService");
        this.pendingHandleStore = Objects.requireNonNull(pendingHandleStore, "pendingHandleStore");
        this.pendingAuthorizationStore = Objects.requireNonNull(pendingAuthorizationStore, "pendingAuthorizationStore");
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthorizationCodeRequestAuthenticationToken request)
                || request.isAuthenticated()) {
            throw rejectUnbound(INVALID_REQUEST_DESCRIPTION);
        }
        CoreAgentAuthorizationEndpointRequestDetails details = requestDetails(request);
        CoreAgentRegisteredClientPolicy policy = requiredPolicy();
        if (!policy.clientId().equals(request.getClientId()) || !policy.redirectUri().equals(request.getRedirectUri())) {
            throw rejectUnbound(INVALID_REQUEST_DESCRIPTION);
        }

        CoreAgentBrowserAuthenticationToken browserAuthentication = browserAuthentication(request);
        CoreAgentBrowserPrincipal browserPrincipal = browserAuthentication.getPrincipal();
        List<String> requestedScopes = requestedScopes(request, details);
        String challenge = requiredAdditionalString(request, "code_challenge");
        String challengeMethod = requiredAdditionalString(request, "code_challenge_method");
        if (!"S256".equals(challengeMethod)) {
            throw rejectBound(request, INVALID_REQUEST_DESCRIPTION);
        }

        EffectiveRolePermissions effectiveRole = effectiveRolePermissionResolver.resolve(browserPrincipal.roleId());
        verifyRoleIdentity(browserPrincipal, effectiveRole, request);
        CoreAgentAuthorizationConsentDecision decision;
        try {
            decision = authorizationConsentService.prepare(browserPrincipal.userId(), effectiveRole, policy,
                    requestedScopes);
        } catch (IllegalArgumentException exception) {
            throw rejectBound(request, INVALID_REQUEST_DESCRIPTION);
        } catch (CoreAgentAuthorizationCodeIssueRejectedException exception) {
            throw rejectBound(request, ACCESS_DENIED_DESCRIPTION);
        }
        if (decision == null) {
            throw new IllegalStateException("CORE AGENT consent preparation returned null");
        }

        if (decision.consentRequired()) {
            retainConsentPending(policy, browserPrincipal, request, details, requestedScopes, challenge, challengeMethod);
            return new OAuth2AuthorizationConsentAuthenticationToken(request.getAuthorizationUri(), policy.clientId(),
                    browserAuthentication, request.getState(),
                    new LinkedHashSet<>(decision.options().preselectedOptionalScopes()), Map.of());
        }

        IssuedCoreAgentAuthorizationCode issued = issueImmediately(browserPrincipal, policy, request, details,
                requestedScopes, challenge, challengeMethod, decision.reusedFinalScopes());
        return new OAuth2AuthorizationCodeRequestAuthenticationToken(request.getAuthorizationUri(), policy.clientId(),
                browserAuthentication, new OAuth2AuthorizationCode(issued.rawCode(),
                issued.expiresAt().minus(policy.authorizationCodeTimeToLive()), issued.expiresAt()),
                policy.redirectUri(), request.getState(), new LinkedHashSet<>(decision.reusedFinalScopes()));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2AuthorizationCodeRequestAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private CoreAgentAuthorizationEndpointRequestDetails requestDetails(
            OAuth2AuthorizationCodeRequestAuthenticationToken request) {
        if (!(request.getDetails() instanceof CoreAgentAuthorizationEndpointRequestDetails details)
                || details.consentAction() != null || details.session() == null
                || details.sessionId() == null || !details.sessionId().equals(details.session().getId())) {
            throw rejectUnbound(INVALID_REQUEST_DESCRIPTION);
        }
        return details;
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

    private static CoreAgentBrowserAuthenticationToken browserAuthentication(
            OAuth2AuthorizationCodeRequestAuthenticationToken request) {
        if (!(request.getPrincipal() instanceof CoreAgentBrowserAuthenticationToken browserAuthentication)
                || !browserAuthentication.isAuthenticated() || browserAuthentication.getCredentials() != null
                || browserAuthentication.getPrincipal() == null
                || !Long.toString(browserAuthentication.getPrincipal().userId()).equals(browserAuthentication.getName())) {
            throw rejectBound(request, ACCESS_DENIED_DESCRIPTION);
        }
        return browserAuthentication;
    }

    private static List<String> requestedScopes(OAuth2AuthorizationCodeRequestAuthenticationToken request,
                                                CoreAgentAuthorizationEndpointRequestDetails details) {
        Set<String> scopes = request.getScopes();
        if (scopes == null) {
            throw rejectBound(request, INVALID_REQUEST_DESCRIPTION);
        }
        if (!details.originalScopeParameterPresent()) {
            if (!scopes.isEmpty()) {
                throw rejectBound(request, INVALID_REQUEST_DESCRIPTION);
            }
            return null;
        }
        if (scopes.isEmpty()) {
            throw rejectBound(request, INVALID_REQUEST_DESCRIPTION);
        }
        return List.copyOf(scopes);
    }

    private static String requiredAdditionalString(OAuth2AuthorizationCodeRequestAuthenticationToken request,
                                                   String name) {
        Object value = request.getAdditionalParameters().get(name);
        if (!(value instanceof String string) || string.isBlank() || !string.equals(string.trim())) {
            throw rejectBound(request, INVALID_REQUEST_DESCRIPTION);
        }
        return string;
    }

    private static void verifyRoleIdentity(CoreAgentBrowserPrincipal principal,
                                           EffectiveRolePermissions effectiveRole,
                                           OAuth2AuthorizationCodeRequestAuthenticationToken request) {
        if (effectiveRole == null || !principal.roleId().equals(effectiveRole.roleId())
                || !principal.roleCode().equals(effectiveRole.roleCode())
                || !principal.rank().equals(effectiveRole.roleRank())) {
            throw new IllegalStateException("CORE AGENT browser role identity is inconsistent");
        }
    }

    private void retainConsentPending(CoreAgentRegisteredClientPolicy policy,
                                      CoreAgentBrowserPrincipal principal,
                                      OAuth2AuthorizationCodeRequestAuthenticationToken request,
                                      CoreAgentAuthorizationEndpointRequestDetails details,
                                      List<String> requestedScopes,
                                      String challenge,
                                      String challengeMethod) {
        CoreAgentPreparedPendingAuthorization prepared;
        try {
            prepared = authorizationCodeIssueService.createPending(issueRequest(principal, policy, request, details,
                    requestedScopes, challenge, challengeMethod), details.sessionId());
            if (prepared == null || prepared.handle() == null || prepared.state() == null
                    || !details.sessionId().equals(prepared.state().sessionId())) {
                throw new IllegalStateException("CORE AGENT pending authorization creation returned inconsistent data");
            }
        } catch (IllegalArgumentException exception) {
            throw rejectBound(request, INVALID_REQUEST_DESCRIPTION);
        } catch (CoreAgentAuthorizationCodeIssueRejectedException exception) {
            throw rejectBound(request, ACCESS_DENIED_DESCRIPTION);
        }
        try {
            pendingHandleStore.replace(details.session(), prepared.handle());
        } catch (RuntimeException exception) {
            try {
                pendingAuthorizationStore.delete(prepared.handle().rawHandle());
            } catch (RuntimeException ignored) {
                // The original session-store failure remains authoritative; Redis TTL is the fallback cleanup.
            }
            if (exception instanceof IllegalArgumentException) {
                throw rejectBound(request, INVALID_REQUEST_DESCRIPTION);
            }
            throw exception;
        }
    }

    private IssuedCoreAgentAuthorizationCode issueImmediately(CoreAgentBrowserPrincipal principal,
                                                               CoreAgentRegisteredClientPolicy policy,
                                                               OAuth2AuthorizationCodeRequestAuthenticationToken request,
                                                               CoreAgentAuthorizationEndpointRequestDetails details,
                                                               List<String> requestedScopes,
                                                               String challenge,
                                                               String challengeMethod,
                                                               Collection<String> grantedScopes) {
        try {
            CoreAgentPreparedPendingAuthorization prepared = authorizationCodeIssueService.createPending(
                    issueRequest(principal, policy, request, details, requestedScopes, challenge, challengeMethod),
                    details.sessionId());
            if (prepared == null || prepared.handle() == null || prepared.state() == null
                    || !details.sessionId().equals(prepared.state().sessionId())) {
                throw new IllegalStateException("CORE AGENT pending authorization creation returned inconsistent data");
            }
            IssuedCoreAgentAuthorizationCode issued = authorizationCodeIssueService.convertPending(
                    new CoreAgentPendingAuthorizationConversionRequest(prepared.handle().rawHandle(), principal.userId(),
                            details.sessionId(), policy.clientId(), policy.redirectUri(), request.getState(),
                            List.copyOf(grantedScopes)));
            if (issued == null) {
                throw new IllegalStateException("CORE AGENT authorization-code issue service returned null");
            }
            return issued;
        } catch (CoreAgentAuthorizationCodeIssueRejectedException exception) {
            throw rejectBound(request, ACCESS_DENIED_DESCRIPTION);
        } catch (IllegalArgumentException exception) {
            throw rejectBound(request, INVALID_REQUEST_DESCRIPTION);
        }
    }

    private static CoreAgentAuthorizationCodeIssueRequest issueRequest(CoreAgentBrowserPrincipal principal,
                                                                        CoreAgentRegisteredClientPolicy policy,
                                                                        OAuth2AuthorizationCodeRequestAuthenticationToken request,
                                                                        CoreAgentAuthorizationEndpointRequestDetails details,
                                                                        List<String> requestedScopes,
                                                                        String challenge,
                                                                        String challengeMethod) {
        return new CoreAgentAuthorizationCodeIssueRequest(principal.userId(), policy.clientId(), policy.redirectUri(),
                requestedScopes, List.of(), challenge, challengeMethod, details.socketRemoteAddress(), request.getState());
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationException rejectUnbound(String description) {
        return new OAuth2AuthorizationCodeRequestAuthenticationException(error(OAuth2ErrorCodes.INVALID_REQUEST,
                description), null);
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationException rejectBound(
            OAuth2AuthorizationCodeRequestAuthenticationToken request, String description) {
        String errorCode = ACCESS_DENIED_DESCRIPTION.equals(description) ? OAuth2ErrorCodes.ACCESS_DENIED
                : OAuth2ErrorCodes.INVALID_REQUEST;
        return new OAuth2AuthorizationCodeRequestAuthenticationException(error(errorCode, description), request);
    }

    private static OAuth2Error error(String code, String description) {
        return new OAuth2Error(code, description, null);
    }
}
