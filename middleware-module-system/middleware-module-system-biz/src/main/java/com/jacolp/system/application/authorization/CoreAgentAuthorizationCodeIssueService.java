package com.jacolp.system.application.authorization;

import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.common.security.oauth2.token.SecureOAuth2TokenGenerator;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.system.application.port.out.CoreAgentPendingAuthorizationCodeTransitionStore;
import com.jacolp.system.application.port.out.CoreAgentPendingAuthorizationStore;
import com.jacolp.constant.UserConstant;
import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationAccountSnapshot;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationCodeIssueRequest;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationCodeState;
import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationConversionRequest;
import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationState;
import com.jacolp.system.application.authorization.model.CoreAgentPreparedPendingAuthorization;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationCode;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationPendingHandle;
import com.jacolp.system.application.authorization.model.PermissionScopePattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Creates pending browser authorization state and converts it atomically into a CORE AGENT code. */
@Service
public final class CoreAgentAuthorizationCodeIssueService {

    private final Clock clock;
    private final SecureOAuth2TokenGenerator tokenGenerator;
    private final CoreAgentRegisteredClientPolicyResolver policyResolver;
    private final AuthorizationAccountRepository accountRepository;
    private final AccountGrantTypeResolver accountGrantTypeResolver;
    private final EffectiveRolePermissionResolver rolePermissionResolver;
    private final CoreAgentConsentScopeService consentScopeService;
    private final CoreAgentPendingAuthorizationHandleGenerator pendingHandleGenerator;
    private final CoreAgentPendingAuthorizationStore pendingAuthorizationStore;
    private final CoreAgentPendingAuthorizationCodeTransitionStore transitionStore;

    @Autowired
    public CoreAgentAuthorizationCodeIssueService(
            SecureOAuth2TokenGenerator tokenGenerator,
            CoreAgentRegisteredClientPolicyResolver policyResolver,
            AuthorizationAccountRepository accountRepository,
            AccountGrantTypeResolver accountGrantTypeResolver,
            EffectiveRolePermissionResolver rolePermissionResolver,
            CoreAgentConsentScopeService consentScopeService,
            CoreAgentPendingAuthorizationHandleGenerator pendingHandleGenerator,
            CoreAgentPendingAuthorizationStore pendingAuthorizationStore,
            CoreAgentPendingAuthorizationCodeTransitionStore transitionStore) {
        this(Clock.systemUTC(), tokenGenerator, policyResolver, accountRepository, accountGrantTypeResolver,
                rolePermissionResolver, consentScopeService, pendingHandleGenerator, pendingAuthorizationStore,
                transitionStore);
    }

    CoreAgentAuthorizationCodeIssueService(
            Clock clock,
            SecureOAuth2TokenGenerator tokenGenerator,
            CoreAgentRegisteredClientPolicyResolver policyResolver,
            AuthorizationAccountRepository accountRepository,
            AccountGrantTypeResolver accountGrantTypeResolver,
            EffectiveRolePermissionResolver rolePermissionResolver,
            CoreAgentConsentScopeService consentScopeService,
            CoreAgentPendingAuthorizationHandleGenerator pendingHandleGenerator,
            CoreAgentPendingAuthorizationStore pendingAuthorizationStore,
            CoreAgentPendingAuthorizationCodeTransitionStore transitionStore) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator");
        this.policyResolver = Objects.requireNonNull(policyResolver, "policyResolver");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.accountGrantTypeResolver = Objects.requireNonNull(accountGrantTypeResolver, "accountGrantTypeResolver");
        this.rolePermissionResolver = Objects.requireNonNull(rolePermissionResolver, "rolePermissionResolver");
        this.consentScopeService = Objects.requireNonNull(consentScopeService, "consentScopeService");
        this.pendingHandleGenerator = Objects.requireNonNull(pendingHandleGenerator, "pendingHandleGenerator");
        this.pendingAuthorizationStore = Objects.requireNonNull(pendingAuthorizationStore, "pendingAuthorizationStore");
        this.transitionStore = Objects.requireNonNull(transitionStore, "transitionStore");
    }

    /**
     * Compatibility bridge for the pre-provider call site. It deliberately uses the same pending
     * and atomic transition path as browser consent; C2 supplies the real servlet session id to
     * {@link #createPending(CoreAgentAuthorizationCodeIssueRequest, String)} instead.
     */
    public IssuedCoreAgentAuthorizationCode issue(CoreAgentAuthorizationCodeIssueRequest request) {
        Objects.requireNonNull(request, "request");
        String immediateSessionBinding = nextOpaque("pending session binding");
        CoreAgentPreparedPendingAuthorization prepared = createPending(request, immediateSessionBinding);
        AccountContext context = currentContext(request.authenticatedUserId(), request.clientId(), request.redirectUri(),
                request.socketRemoteAddress());
        List<String> finalScopes = confirmScopes(context.effectiveRole(), context.policy(), request.requestedScopes(),
                request.submittedOptionalScopes());
        return convertPending(new CoreAgentPendingAuthorizationConversionRequest(prepared.handle().rawHandle(),
                request.authenticatedUserId(), immediateSessionBinding, request.clientId(), request.redirectUri(),
                request.oauthState(), finalScopes));
    }

    /** Creates only Redis pending state; callers retain its opaque handle in their browser session. */
    public CoreAgentPreparedPendingAuthorization createPending(
            CoreAgentAuthorizationCodeIssueRequest request, String sessionId) {
        Objects.requireNonNull(request, "request");
        sessionId = CoreAgentPendingAuthorizationState.requireSessionId(sessionId);
        AccountContext context = currentContext(request.authenticatedUserId(), request.clientId(), request.redirectUri(),
                request.socketRemoteAddress());
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(context.policy().authorizationCodeTimeToLive());
        CoreAgentPendingAuthorizationState pending = new CoreAgentPendingAuthorizationState(context.policy().clientId(),
                context.policy().redirectUri(), request.requestedScopes(), request.codeChallenge(),
                request.codeChallengeMethod(), request.oauthState(), request.socketRemoteAddress(),
                context.account().userId(), sessionId, issuedAt, expiresAt);
        var handle = pendingHandleGenerator.generate(expiresAt);
        if (handle == null || !expiresAt.equals(handle.expiresAt())) {
            throw new IllegalStateException("CORE AGENT pending handle generator returned an inconsistent handle");
        }
        pendingAuthorizationStore.save(handle, pending);
        return new CoreAgentPreparedPendingAuthorization(handle, pending);
    }

    /**
     * Revalidates current account, policy, role, pending binding, and final scopes before the
     * only normal authorization-code persistence path: atomic pending-to-code conversion.
     */
    public IssuedCoreAgentAuthorizationCode convertPending(CoreAgentPendingAuthorizationConversionRequest request) {
        Objects.requireNonNull(request, "request");
        Optional<CoreAgentPendingAuthorizationState> pendingOptional = pendingAuthorizationStore.find(
                request.rawPendingHandle());
        if (pendingOptional == null) {
            throw new IllegalStateException("CORE AGENT pending authorization lookup returned null");
        }
        if (pendingOptional.isEmpty()) {
            throw rejected();
        }
        CoreAgentPendingAuthorizationState pending = pendingOptional.get();
        verifyPendingBinding(pending, request);
        Instant now = clock.instant();
        if (pending.issuedAt().isAfter(now) || !now.isBefore(pending.expiresAt())) {
            throw rejected();
        }
        AccountContext context = currentContext(request.authenticatedUserId(), request.clientId(), request.redirectUri(),
                pending.originalSocketAddress());
        List<String> finalScopes = validateGrantedScopes(context.effectiveRole(), context.policy(),
                pending.requestedScopes(), request.grantedScopes());
        String rawCode = nextOpaque("authorization-code");
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(context.policy().authorizationCodeTimeToLive());
        CoreAgentAuthorizationCodeState codeState = new CoreAgentAuthorizationCodeState(rawCode, context.policy().clientId(),
                context.policy().redirectUri(), finalScopes, pending.codeChallenge(), pending.codeChallengeMethod(),
                pending.originalSocketAddress(), pending.oauthState(), issuedAt, expiresAt, snapshot(context.account()));
        var pendingHandle = new IssuedCoreAgentAuthorizationPendingHandle(request.rawPendingHandle(), pending.expiresAt());
        if (!transitionStore.consumePendingAndStoreCode(pendingHandle, pending, codeState)) {
            throw rejected();
        }
        return new IssuedCoreAgentAuthorizationCode(rawCode, expiresAt);
    }

    private CoreAgentRegisteredClientPolicy requiredPolicy() {
        CoreAgentRegisteredClientPolicy policy = policyResolver.resolve(
                CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID);
        if (policy == null || !CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID.equals(policy.clientId())) {
            throw new IllegalStateException("CORE AGENT registered client policy is invalid");
        }
        return policy;
    }

    private AccountContext currentContext(Long userId, String clientId, String redirectUri, String socketRemoteAddress) {
        CoreAgentRegisteredClientPolicy policy = requiredPolicy();
        if (!policy.clientId().equals(clientId) || !policy.redirectUri().equals(redirectUri)) {
            throw rejected();
        }
        enforceSocketAllowed(policy, socketRemoteAddress);
        AuthorizationAccount account = currentAccount(userId);
        verifyAuthorizationCodeGrant(account);
        EffectiveRolePermissions effectiveRole = rolePermissionResolver.resolve(account.roleId());
        if (effectiveRole == null || !account.roleId().equals(effectiveRole.roleId())) {
            throw new IllegalStateException("CORE AGENT effective role identity is inconsistent");
        }
        return new AccountContext(policy, account, effectiveRole);
    }

    private List<String> confirmScopes(EffectiveRolePermissions effectiveRole,
                                       CoreAgentRegisteredClientPolicy policy,
                                       Collection<String> requestedScopes,
                                       Collection<String> submittedOptionalScopes) {
        List<String> scopes = consentScopeService.confirm(effectiveRole, policy, requestedScopes, null,
                submittedOptionalScopes);
        if (scopes == null || scopes.isEmpty()) {
            throw new IllegalStateException("CORE AGENT consent scope resolution returned no scopes");
        }
        return scopes;
    }

    private static void verifyPendingBinding(CoreAgentPendingAuthorizationState pending,
                                             CoreAgentPendingAuthorizationConversionRequest request) {
        if (pending.authenticatedUserId() != request.authenticatedUserId()
                || !pending.sessionId().equals(request.sessionId())
                || !pending.clientId().equals(request.clientId())
                || !pending.redirectUri().equals(request.redirectUri())
                || !pending.oauthState().equals(request.oauthState())) {
            throw rejected();
        }
    }

    private List<String> validateGrantedScopes(EffectiveRolePermissions effectiveRole,
                                               CoreAgentRegisteredClientPolicy policy,
                                               Collection<String> requestedScopes,
                                               Collection<String> grantedScopes) {
        if (grantedScopes == null || grantedScopes.isEmpty()) {
            throw rejected();
        }
        LinkedHashSet<String> canonical = new LinkedHashSet<>();
        for (String scope : grantedScopes) {
            try {
                if (scope == null || scope.isBlank() || !scope.equals(scope.trim())
                        || !canonical.add(PermissionScopePattern.parse(scope).asScope())) {
                    throw rejected();
                }
            } catch (IllegalArgumentException exception) {
                throw rejected();
            }
        }
        List<String> normalized = new ArrayList<>(canonical);
        normalized.sort(String::compareTo);
        var options = consentScopeService.options(effectiveRole, policy, requestedScopes, null);
        LinkedHashSet<String> allowed = new LinkedHashSet<>(options.candidateScopes());
        allowed.addAll(options.mandatoryScopes());
        if (!allowed.containsAll(normalized) || !normalized.containsAll(options.mandatoryScopes())) {
            throw rejected();
        }
        return List.copyOf(normalized);
    }

    private String nextOpaque(String purpose) {
        String value = tokenGenerator.newOpaqueToken();
        if (value == null) {
            throw new IllegalStateException("CORE AGENT " + purpose + " generator returned null");
        }
        try {
            IssuedCoreAgentAuthorizationPendingHandle.requireRawHandle(value);
            return value;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("CORE AGENT " + purpose + " generator returned an invalid value", exception);
        }
    }

    private static void enforceSocketAllowed(CoreAgentRegisteredClientPolicy policy, String socketRemoteAddress) {
        final ClientAllowedIpPolicy allowedIps;
        try {
            allowedIps = ClientAllowedIpPolicy.parse(policy.allowedIps());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("CORE AGENT allowed IP policy is invalid", exception);
        }
        try {
            if (!allowedIps.allows(socketRemoteAddress)) {
                throw rejected();
            }
        } catch (IllegalArgumentException exception) {
            throw rejected();
        }
    }

    private AuthorizationAccount currentAccount(Long expectedUserId) {
        Optional<AuthorizationAccount> accountOptional = accountRepository.findById(expectedUserId);
        if (accountOptional == null) {
            throw new IllegalStateException("CORE AGENT authorization account lookup returned null");
        }
        if (accountOptional.isEmpty()) {
            throw rejected();
        }
        AuthorizationAccount account = accountOptional.get();
        if (!expectedUserId.equals(account.userId())) {
            throw new IllegalStateException("CORE AGENT authorization account identity is inconsistent");
        }
        return account;
    }

    private void verifyAuthorizationCodeGrant(AuthorizationAccount account) {
        if (account.status() != UserConstant.ACTIVE_STATUS) {
            throw rejected();
        }
        final boolean allowed;
        try {
            allowed = accountGrantTypeResolver.allows(AccountGrantTypeResolver.AUTHORIZATION_CODE,
                    account.extraGrantTypes());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("CORE AGENT authorization account grant configuration is invalid");
        }
        if (!allowed) {
            throw rejected();
        }
    }

    private static CoreAgentAuthorizationAccountSnapshot snapshot(AuthorizationAccount account) {
        return new CoreAgentAuthorizationAccountSnapshot(account.userId(), account.username(), account.roleId(),
                account.passwordHash(), account.email(), account.extraGrantTypes(), account.status());
    }

    private static CoreAgentAuthorizationCodeIssueRejectedException rejected() {
        return new CoreAgentAuthorizationCodeIssueRejectedException();
    }

    private record AccountContext(
            CoreAgentRegisteredClientPolicy policy,
            AuthorizationAccount account,
            EffectiveRolePermissions effectiveRole) {
    }
}
