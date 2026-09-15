package com.jacolp.system.application.authorization;

import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.constant.UserConstant;
import com.jacolp.common.security.oauth2.token.AccessTokenIssueRequest;
import com.jacolp.common.security.oauth2.token.AccessTokenSessionReference;
import com.jacolp.common.security.oauth2.token.IssuedAccessToken;
import com.jacolp.common.security.oauth2.token.IssuedRefreshToken;
import com.jacolp.common.security.oauth2.token.OAuth2RefreshTokenSessionService;
import com.jacolp.common.security.oauth2.token.Rs256AccessTokenIssuer;
import com.jacolp.common.security.oauth2.token.VerifiedRefreshToken;
import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.authorization.model.CoreAgentRefreshTokenRequest;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentRefreshTokens;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reauthorizes and rotates a CORE AGENT refresh session without trusting its persisted scopes as current rights.
 */
@Service
public final class CoreAgentRefreshTokenService {

    private final CoreAgentRegisteredClientPolicyResolver policyResolver;
    private final AuthorizationAccountRepository accountRepository;
    private final AccountGrantTypeResolver accountGrantTypeResolver;
    private final EffectiveRolePermissionResolver rolePermissionResolver;
    private final OAuth2ScopeResolver scopeResolver;
    private final Rs256AccessTokenIssuer accessTokenIssuer;
    private final OAuth2RefreshTokenSessionService refreshTokenSessionService;

    public CoreAgentRefreshTokenService(
            CoreAgentRegisteredClientPolicyResolver policyResolver,
            AuthorizationAccountRepository accountRepository,
            AccountGrantTypeResolver accountGrantTypeResolver,
            EffectiveRolePermissionResolver rolePermissionResolver,
            OAuth2ScopeResolver scopeResolver,
            Rs256AccessTokenIssuer accessTokenIssuer,
            OAuth2RefreshTokenSessionService refreshTokenSessionService) {
        this.policyResolver = Objects.requireNonNull(policyResolver, "policyResolver");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.accountGrantTypeResolver = Objects.requireNonNull(accountGrantTypeResolver, "accountGrantTypeResolver");
        this.rolePermissionResolver = Objects.requireNonNull(rolePermissionResolver, "rolePermissionResolver");
        this.scopeResolver = Objects.requireNonNull(scopeResolver, "scopeResolver");
        this.accessTokenIssuer = Objects.requireNonNull(accessTokenIssuer, "accessTokenIssuer");
        this.refreshTokenSessionService = Objects.requireNonNull(refreshTokenSessionService,
                "refreshTokenSessionService");
    }

    /**
     * Issues a fresh access token only if rotation atomically replaces the verified current refresh session.
     * A refresh-store failure or compare-and-set miss returns no access-token response.
     */
    public IssuedCoreAgentRefreshTokens refresh(CoreAgentRefreshTokenRequest request) {
        Objects.requireNonNull(request, "request");
        CoreAgentRegisteredClientPolicy policy = requiredPolicy(request);
        enforceSocketAllowed(policy, request.socketRemoteAddress());
        VerifiedRefreshToken verifiedRefresh = verifiedRefresh(request.rawRefreshToken());
        if (!policy.clientId().equals(verifiedRefresh.clientId())) {
            throw rejected();
        }
        AuthorizationAccount account = currentAccount(verifiedRefresh.userId());
        verifyAuthorizationCodeGrant(account);
        EffectiveRolePermissions effectiveRole = currentEffectiveRole(account);
        List<String> grantedScopes = currentGrantedScopes(effectiveRole, policy, verifiedRefresh.grantedScopes(),
                request.requestedScopes());

        IssuedAccessToken accessToken = accessTokenIssuer.issue(new AccessTokenIssueRequest(account.userId(),
                policy.clientId(), AccountGrantTypeResolver.REFRESH_TOKEN, account.username(), effectiveRole.roleCode(),
                Set.copyOf(grantedScopes), policy.accessTokenTimeToLive()));
        if (accessToken == null) {
            throw new IllegalStateException("CORE AGENT refresh access token issuer returned null");
        }
        Optional<IssuedRefreshToken> rotated = refreshTokenSessionService.rotate(request.rawRefreshToken(),
                new AccessTokenSessionReference(accessToken.jti(), accessToken.expiresAt()), grantedScopes,
                policy.refreshTokenTimeToLive());
        if (rotated == null) {
            throw new IllegalStateException("CORE AGENT refresh rotation returned null");
        }
        if (rotated.isEmpty()) {
            throw rejected();
        }
        IssuedRefreshToken refreshToken = rotated.get();
        return new IssuedCoreAgentRefreshTokens(accessToken.tokenValue(), refreshToken.rawToken(), "Bearer",
                accessToken.issuedAt(), accessToken.expiresAt(), refreshToken.issuedAt(), refreshToken.expiresAt(),
                grantedScopes);
    }

    private CoreAgentRegisteredClientPolicy requiredPolicy(CoreAgentRefreshTokenRequest request) {
        CoreAgentRegisteredClientPolicy policy = policyResolver.resolve(
                CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID);
        if (policy == null || !CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID.equals(policy.clientId())
                || !request.clientId().equals(policy.clientId())) {
            throw rejected();
        }
        return policy;
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

    private VerifiedRefreshToken verifiedRefresh(String rawRefreshToken) {
        Optional<VerifiedRefreshToken> verified = refreshTokenSessionService.verify(rawRefreshToken);
        if (verified == null) {
            throw new IllegalStateException("CORE AGENT refresh verification returned null");
        }
        return verified.orElseThrow(CoreAgentRefreshTokenService::rejected);
    }

    private AuthorizationAccount currentAccount(long userId) {
        Optional<AuthorizationAccount> accountOptional = accountRepository.findById(userId);
        if (accountOptional == null) {
            throw new IllegalStateException("CORE AGENT refresh account lookup returned null");
        }
        if (accountOptional.isEmpty()) {
            throw rejected();
        }
        AuthorizationAccount account = accountOptional.get();
        if (account.userId() != userId) {
            throw new IllegalStateException("CORE AGENT refresh account identity is inconsistent");
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
            throw new IllegalStateException("CORE AGENT refresh account grant configuration is invalid", exception);
        }
        if (!allowed) {
            throw rejected();
        }
    }

    private EffectiveRolePermissions currentEffectiveRole(AuthorizationAccount account) {
        EffectiveRolePermissions effectiveRole = rolePermissionResolver.resolve(account.roleId());
        if (effectiveRole == null || !account.roleId().equals(effectiveRole.roleId())
                || effectiveRole.roleCode() == null || effectiveRole.roleCode().isBlank()
                || effectiveRole.roleRank() == null || effectiveRole.roleRank() <= 0) {
            throw new IllegalStateException("CORE AGENT refresh effective role identity is inconsistent");
        }
        return effectiveRole;
    }

    private List<String> currentGrantedScopes(EffectiveRolePermissions effectiveRole,
                                              CoreAgentRegisteredClientPolicy policy,
                                              List<String> priorGrantedScopes,
                                              List<String> requestedScopes) {
        List<String> baseScopes = scopeResolver.resolve(effectiveRole, policy.scopes(), policy.autoApproveScopes(),
                priorGrantedScopes);
        if (baseScopes == null) {
            throw new IllegalStateException("CORE AGENT refresh scope resolution returned null");
        }
        List<String> scopes = requestedScopes == null ? baseScopes
                : scopeResolver.narrowGrantedScopes(baseScopes, requestedScopes);
        if (scopes == null) {
            throw new IllegalStateException("CORE AGENT refresh scope narrowing returned null");
        }
        if (scopes.isEmpty()) {
            throw rejected();
        }
        return scopes;
    }

    private static CoreAgentRefreshTokenRejectedException rejected() {
        return new CoreAgentRefreshTokenRejectedException();
    }
}
