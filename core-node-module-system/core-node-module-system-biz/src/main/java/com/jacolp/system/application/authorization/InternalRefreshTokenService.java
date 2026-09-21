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
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.InternalIssuedTokens;
import com.jacolp.system.application.authorization.model.InternalRefreshTokenRequest;
import com.jacolp.system.application.authorization.model.InternalRegisteredClientPolicy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reauthorizes and atomically rotates a USER/ADMIN refresh session without trusting persisted scopes as current
 * rights. Refresh remains available only through the internal login route, never through {@code /oauth/token}.
 */
@Service
public class InternalRefreshTokenService {

    private final InternalRegisteredClientPolicyResolver policyResolver;
    private final AuthorizationAccountRepository accountRepository;
    private final EffectiveRolePermissionResolver rolePermissionResolver;
    private final OAuth2ScopeResolver scopeResolver;
    private final Rs256AccessTokenIssuer accessTokenIssuer;
    private final OAuth2RefreshTokenSessionService refreshTokenSessionService;

    public InternalRefreshTokenService(InternalRegisteredClientPolicyResolver policyResolver,
                                       AuthorizationAccountRepository accountRepository,
                                       EffectiveRolePermissionResolver rolePermissionResolver,
                                       OAuth2ScopeResolver scopeResolver,
                                       Rs256AccessTokenIssuer accessTokenIssuer,
                                       OAuth2RefreshTokenSessionService refreshTokenSessionService) {
        this.policyResolver = Objects.requireNonNull(policyResolver, "policyResolver");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.rolePermissionResolver = Objects.requireNonNull(rolePermissionResolver, "rolePermissionResolver");
        this.scopeResolver = Objects.requireNonNull(scopeResolver, "scopeResolver");
        this.accessTokenIssuer = Objects.requireNonNull(accessTokenIssuer, "accessTokenIssuer");
        this.refreshTokenSessionService = Objects.requireNonNull(refreshTokenSessionService,
                "refreshTokenSessionService");
    }

    /** Issues a fresh access token only after refresh rotation atomically replaces the verified session. */
    public InternalIssuedTokens refresh(InternalRefreshTokenRequest request) {
        Objects.requireNonNull(request, "request");
        InternalRegisteredClientPolicy policy = requiredPolicy(request);
        enforceSocketAllowed(policy, request.socketRemoteAddress());
        VerifiedRefreshToken verifiedRefresh = verifiedRefresh(request.rawRefreshToken());
        if (!policy.clientId().equals(verifiedRefresh.clientId())) {
            throw rejected();
        }
        AuthorizationAccount account = currentAccount(verifiedRefresh.userId());
        EffectiveRolePermissions effectiveRole = currentEffectiveRole(account, policy);
        List<String> grantedScopes = currentGrantedScopes(effectiveRole, policy, verifiedRefresh.grantedScopes(),
                request.requestedScopes());

        IssuedAccessToken accessToken = accessTokenIssuer.issue(new AccessTokenIssueRequest(account.userId(),
                policy.clientId(), AccountGrantTypeResolver.REFRESH_TOKEN, account.username(), effectiveRole.roleCode(),
                Set.copyOf(grantedScopes), policy.accessTokenTimeToLive()));
        if (accessToken == null) {
            throw new IllegalStateException("Internal refresh access token issuer returned null");
        }
        Optional<IssuedRefreshToken> rotated = refreshTokenSessionService.rotate(request.rawRefreshToken(),
                new AccessTokenSessionReference(accessToken.jti(), accessToken.expiresAt()), grantedScopes,
                policy.refreshTokenTimeToLive());
        if (rotated == null) {
            throw new IllegalStateException("Internal refresh rotation returned null");
        }
        if (rotated.isEmpty()) {
            throw rejected();
        }
        IssuedRefreshToken refreshToken = rotated.get();
        return new InternalIssuedTokens(accessToken.tokenValue(), refreshToken.rawToken(), "Bearer", accessToken.issuedAt(),
                accessToken.expiresAt(), refreshToken.expiresAt(), grantedScopes);
    }

    private InternalRegisteredClientPolicy requiredPolicy(InternalRefreshTokenRequest request) {
        InternalRegisteredClientPolicy policy = policyResolver.resolveRefresh(request.clientId());
        if (policy == null || !request.clientId().equals(policy.clientId())
                || !AccountGrantTypeResolver.REFRESH_TOKEN.equals(policy.grantType())) {
            throw new IllegalStateException("Internal refresh policy is invalid");
        }
        return policy;
    }

    private static void enforceSocketAllowed(InternalRegisteredClientPolicy policy, String socketRemoteAddress) {
        final ClientAllowedIpPolicy allowedIps;
        try {
            allowedIps = ClientAllowedIpPolicy.parse(policy.allowedIps());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Internal refresh allowed IP policy is invalid", exception);
        }
        try {
            if (!allowedIps.allows(socketRemoteAddress)) {
                throw ipRejected();
            }
        } catch (IllegalArgumentException exception) {
            throw ipRejected();
        }
    }

    private VerifiedRefreshToken verifiedRefresh(String rawRefreshToken) {
        Optional<VerifiedRefreshToken> verified = refreshTokenSessionService.verify(rawRefreshToken);
        if (verified == null) {
            throw new IllegalStateException("Internal refresh verification returned null");
        }
        return verified.orElseThrow(InternalRefreshTokenService::rejected);
    }

    private AuthorizationAccount currentAccount(long userId) {
        Optional<AuthorizationAccount> accountOptional = accountRepository.findById(userId);
        if (accountOptional == null) {
            throw new IllegalStateException("Internal refresh account lookup returned null");
        }
        if (accountOptional.isEmpty()) {
            throw rejected();
        }
        AuthorizationAccount account = accountOptional.get();
        if (account.userId() != userId) {
            throw new IllegalStateException("Internal refresh account identity is inconsistent");
        }
        if (account.status() != UserConstant.ACTIVE_STATUS) {
            throw accountStatusRejected(account.status());
        }
        return account;
    }

    private EffectiveRolePermissions currentEffectiveRole(AuthorizationAccount account,
                                                          InternalRegisteredClientPolicy policy) {
        EffectiveRolePermissions effectiveRole = rolePermissionResolver.resolve(account.roleId());
        if (effectiveRole == null || !account.roleId().equals(effectiveRole.roleId())
                || effectiveRole.roleCode() == null || effectiveRole.roleCode().isBlank()
                || effectiveRole.roleRank() == null || effectiveRole.roleRank() <= 0) {
            throw new IllegalStateException("Internal refresh effective role identity is inconsistent");
        }
        if (!InternalAccountEligibilityService.isRoleAllowedForClient(policy.clientId(), effectiveRole.roleCode())) {
            throw new InternalAccountAuthenticationRejectedException(
                    InternalAccountAuthenticationRejectedException.Reason.ROLE_NOT_ALLOWED);
        }
        return effectiveRole;
    }

    private List<String> currentGrantedScopes(EffectiveRolePermissions effectiveRole,
                                              InternalRegisteredClientPolicy policy,
                                              List<String> priorGrantedScopes,
                                              List<String> requestedScopes) {
        List<String> baseScopes = scopeResolver.resolve(effectiveRole, policy.scopes(), policy.autoApproveScopes(),
                priorGrantedScopes);
        if (baseScopes == null) {
            throw new IllegalStateException("Internal refresh scope resolution returned null");
        }
        List<String> scopes = requestedScopes == null ? baseScopes
                : scopeResolver.narrowGrantedScopes(baseScopes, requestedScopes);
        if (scopes == null) {
            throw new IllegalStateException("Internal refresh scope narrowing returned null");
        }
        if (scopes.isEmpty()) {
            throw new InternalAccountAuthenticationRejectedException(
                    InternalAccountAuthenticationRejectedException.Reason.NO_EFFECTIVE_PERMISSION);
        }
        return scopes;
    }

    private static InternalAccountAuthenticationRejectedException ipRejected() {
        return new InternalAccountAuthenticationRejectedException(
                InternalAccountAuthenticationRejectedException.Reason.IP_NOT_ALLOWED);
    }

    private static InternalAccountAuthenticationRejectedException accountStatusRejected(Integer status) {
        InternalAccountAuthenticationRejectedException.Reason reason = status != null
                && status == UserConstant.UNACTIVE_STATUS
                ? InternalAccountAuthenticationRejectedException.Reason.ACCOUNT_NOT_ACTIVATED
                : InternalAccountAuthenticationRejectedException.Reason.ACCOUNT_DISABLED;
        return new InternalAccountAuthenticationRejectedException(reason);
    }

    private static InternalRefreshTokenRejectedException rejected() {
        return new InternalRefreshTokenRejectedException();
    }
}
