package com.jacolp.system.web.authorization;

import com.jacolp.system.application.port.out.CoreAgentPendingAuthorizationStore;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationCodeIssueRejectedException;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationCodeIssueService;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationConsentRejectedException;
import com.jacolp.system.application.authorization.CoreAgentAuthorizationConsentService;
import com.jacolp.system.application.authorization.CoreAgentBrowserAuthenticationToken;
import com.jacolp.system.application.authorization.CoreAgentRegisteredClientPolicyResolver;
import com.jacolp.system.application.authorization.EffectiveRolePermissionResolver;
import com.jacolp.system.application.authorization.model.CoreAgentBrowserPrincipal;
import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationConversionRequest;
import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationState;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationCode;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Consumes the browser's CORE AGENT consent submission without creating Spring Authorization
 * Server authorization state. The trusted redirect URI is recovered only from Redis pending
 * state after the opaque session handle, authenticated browser identity, client, and OAuth state
 * are bound together.
 */
@Component
public final class CoreAgentAuthorizationConsentAuthenticationProvider implements AuthenticationProvider {

    static final String INVALID_REQUEST_DESCRIPTION = "Invalid CORE AGENT consent request";
    static final String ACCESS_DENIED_DESCRIPTION = "CORE AGENT authorization denied";

    private final CoreAgentRegisteredClientPolicyResolver policyResolver;
    private final EffectiveRolePermissionResolver effectiveRolePermissionResolver;
    private final CoreAgentAuthorizationConsentService authorizationConsentService;
    private final CoreAgentAuthorizationCodeIssueService authorizationCodeIssueService;
    private final HttpSessionCoreAgentPendingAuthorizationHandleStore pendingHandleStore;
    private final CoreAgentPendingAuthorizationStore pendingAuthorizationStore;

    public CoreAgentAuthorizationConsentAuthenticationProvider(
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
        if (!(authentication instanceof OAuth2AuthorizationConsentAuthenticationToken request)) {
            throw rejectUnbound(INVALID_REQUEST_DESCRIPTION);
        }
        CoreAgentAuthorizationEndpointRequestDetails details = requestDetails(request);
        CoreAgentBrowserAuthenticationToken browserAuthentication = browserAuthentication(request);
        CoreAgentBrowserPrincipal principal = browserAuthentication.getPrincipal();
        if (!CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID.equals(request.getClientId())) {
            throw rejectUnbound(INVALID_REQUEST_DESCRIPTION);
        }

        String rawPendingHandle = pendingHandleStore.find(details.session()).orElseThrow(
                () -> rejectUnbound(INVALID_REQUEST_DESCRIPTION));
        CoreAgentPendingAuthorizationState pending = findPending(rawPendingHandle);
        CoreAgentRegisteredClientPolicy policy = requiredPolicy();
        if (!matchesTrustedPending(request, details, principal, policy, pending)) {
            throw rejectUnbound(INVALID_REQUEST_DESCRIPTION);
        }
        EffectiveRolePermissions effectiveRole = effectiveRolePermissionResolver.resolve(principal.roleId());
        verifyRoleIdentity(principal, effectiveRole);
        OAuth2AuthorizationCodeRequestAuthenticationToken trustedRequest = trustedRequest(request, browserAuthentication,
                policy, pending);

        if (details.consentAction() == CoreAgentAuthorizationEndpointRequestDetails.ConsentAction.DENY) {
            pendingAuthorizationStore.delete(rawPendingHandle);
            removeHandleBestEffort(details, rawPendingHandle);
            throw rejectBound(trustedRequest, ACCESS_DENIED_DESCRIPTION);
        }

        List<String> grantedScopes;
        try {
            grantedScopes = authorizationConsentService.confirm(principal.userId(), effectiveRole, policy,
                    pending.requestedScopes(), requestedOptionalScopes(request));
            if (grantedScopes == null || grantedScopes.isEmpty()) {
                throw new IllegalStateException("CORE AGENT consent confirmation returned no scopes");
            }
        } catch (CoreAgentAuthorizationConsentRejectedException | CoreAgentAuthorizationCodeIssueRejectedException exception) {
            throw rejectBound(trustedRequest, ACCESS_DENIED_DESCRIPTION);
        } catch (IllegalArgumentException exception) {
            throw rejectBound(trustedRequest, ACCESS_DENIED_DESCRIPTION);
        }

        final IssuedCoreAgentAuthorizationCode issued;
        try {
            issued = authorizationCodeIssueService.convertPending(new CoreAgentPendingAuthorizationConversionRequest(
                    rawPendingHandle, principal.userId(), details.sessionId(), policy.clientId(), policy.redirectUri(),
                    pending.oauthState(), grantedScopes));
            if (issued == null) {
                throw new IllegalStateException("CORE AGENT pending conversion returned null");
            }
        } catch (CoreAgentAuthorizationCodeIssueRejectedException exception) {
            throw rejectBound(trustedRequest, ACCESS_DENIED_DESCRIPTION);
        } catch (IllegalArgumentException exception) {
            throw rejectBound(trustedRequest, ACCESS_DENIED_DESCRIPTION);
        }

        removeHandleBestEffort(details, rawPendingHandle);
        return new OAuth2AuthorizationCodeRequestAuthenticationToken(request.getAuthorizationUri(), policy.clientId(),
                browserAuthentication, new OAuth2AuthorizationCode(issued.rawCode(),
                issued.expiresAt().minus(policy.authorizationCodeTimeToLive()), issued.expiresAt()),
                policy.redirectUri(), pending.oauthState(), new LinkedHashSet<>(grantedScopes));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2AuthorizationConsentAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static CoreAgentAuthorizationEndpointRequestDetails requestDetails(
            OAuth2AuthorizationConsentAuthenticationToken request) {
        if (!(request.getDetails() instanceof CoreAgentAuthorizationEndpointRequestDetails details)
                || details.session() == null || details.sessionId() == null
                || !details.sessionId().equals(details.session().getId()) || details.consentAction() == null) {
            throw rejectUnbound(INVALID_REQUEST_DESCRIPTION);
        }
        return details;
    }

    private static CoreAgentBrowserAuthenticationToken browserAuthentication(
            OAuth2AuthorizationConsentAuthenticationToken request) {
        if (!(request.getPrincipal() instanceof CoreAgentBrowserAuthenticationToken browserAuthentication)
                || !browserAuthentication.isAuthenticated() || browserAuthentication.getCredentials() != null
                || browserAuthentication.getPrincipal() == null
                || !Long.toString(browserAuthentication.getPrincipal().userId()).equals(browserAuthentication.getName())) {
            throw rejectUnbound(ACCESS_DENIED_DESCRIPTION);
        }
        return browserAuthentication;
    }

    private CoreAgentPendingAuthorizationState findPending(String rawPendingHandle) {
        Optional<CoreAgentPendingAuthorizationState> pendingOptional = pendingAuthorizationStore.find(rawPendingHandle);
        if (pendingOptional == null) {
            throw new IllegalStateException("CORE AGENT pending authorization lookup returned null");
        }
        return pendingOptional.orElseThrow(() -> rejectUnbound(INVALID_REQUEST_DESCRIPTION));
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

    private static boolean matchesTrustedPending(OAuth2AuthorizationConsentAuthenticationToken request,
                                                 CoreAgentAuthorizationEndpointRequestDetails details,
                                                 CoreAgentBrowserPrincipal principal,
                                                 CoreAgentRegisteredClientPolicy policy,
                                                 CoreAgentPendingAuthorizationState pending) {
        return pending != null
                && pending.clientId().equals(policy.clientId())
                && pending.redirectUri().equals(policy.redirectUri())
                && pending.clientId().equals(request.getClientId())
                && pending.oauthState().equals(request.getState())
                && pending.authenticatedUserId() == principal.userId()
                && pending.sessionId().equals(details.sessionId());
    }

    private static void verifyRoleIdentity(CoreAgentBrowserPrincipal principal,
                                           EffectiveRolePermissions effectiveRole) {
        if (effectiveRole == null || !principal.roleId().equals(effectiveRole.roleId())
                || !principal.roleCode().equals(effectiveRole.roleCode())
                || !principal.rank().equals(effectiveRole.roleRank())) {
            throw new IllegalStateException("CORE AGENT browser role identity is inconsistent");
        }
    }

    private static List<String> requestedOptionalScopes(OAuth2AuthorizationConsentAuthenticationToken request) {
        Set<String> scopes = request.getScopes();
        if (scopes == null) {
            throw new IllegalArgumentException("CORE AGENT consent scopes are required");
        }
        return List.copyOf(scopes);
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationToken trustedRequest(
            OAuth2AuthorizationConsentAuthenticationToken request,
            CoreAgentBrowserAuthenticationToken browserAuthentication,
            CoreAgentRegisteredClientPolicy policy,
            CoreAgentPendingAuthorizationState pending) {
        return new OAuth2AuthorizationCodeRequestAuthenticationToken(request.getAuthorizationUri(), policy.clientId(),
                browserAuthentication, policy.redirectUri(), pending.oauthState(), Set.of(), java.util.Map.of());
    }

    private void removeHandleBestEffort(CoreAgentAuthorizationEndpointRequestDetails details, String rawPendingHandle) {
        try {
            pendingHandleStore.removeIfMatches(details.session(), rawPendingHandle);
        } catch (RuntimeException ignored) {
            // The Redis code transition/delete is already authoritative; never turn it into a failed OAuth response.
        }
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
