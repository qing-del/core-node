package com.jacolp.system.application.authorization;

import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.constant.UserConstant;
import com.jacolp.common.security.oauth2.token.AccessTokenIssueRequest;
import com.jacolp.common.security.oauth2.token.AccessTokenSessionReference;
import com.jacolp.common.security.oauth2.token.IssuedAccessToken;
import com.jacolp.common.security.oauth2.token.IssuedRefreshToken;
import com.jacolp.common.security.oauth2.token.OAuth2RefreshTokenSessionService;
import com.jacolp.common.security.oauth2.token.RefreshTokenIssueRequest;
import com.jacolp.common.security.oauth2.token.Rs256AccessTokenIssuer;
import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationCodeTokens;
import com.jacolp.system.application.authorization.model.VerifiedCoreAgentAuthorizationCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Re-evaluates current authorization state and emits the token pair for one already-consumed CORE AGENT code.
 *
 * <p>The verified code's consent scopes are a ceiling, never a permission snapshot: role permissions and the
 * fixed client policy are resolved again immediately before signing. This service intentionally has no Spring
 * Authorization Server authorization persistence dependency.</p>
 */
@Service
public final class CoreAgentAuthorizationCodeTokenService {

    private final CoreAgentRegisteredClientPolicyResolver policyResolver;
    private final AuthorizationAccountRepository accountRepository;
    private final AccountGrantTypeResolver accountGrantTypeResolver;
    private final EffectiveRolePermissionResolver rolePermissionResolver;
    private final OAuth2ScopeResolver scopeResolver;
    private final Rs256AccessTokenIssuer accessTokenIssuer;
    private final OAuth2RefreshTokenSessionService refreshTokenSessionService;

    public CoreAgentAuthorizationCodeTokenService(
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
     * Issues access and rotating-refresh credentials only after recomputing the current effective scope ceiling.
     * A refresh-store failure is propagated, so this method never returns an access token without its session.
     */
    public IssuedCoreAgentAuthorizationCodeTokens issue(VerifiedCoreAgentAuthorizationCode verifiedCode) {
        Objects.requireNonNull(verifiedCode, "verifiedCode");
        CoreAgentRegisteredClientPolicy policy = requiredPolicy(verifiedCode);
        AuthorizationAccount account = currentAccount(verifiedCode);
        verifyAuthorizationCodeGrant(account);
        EffectiveRolePermissions effectiveRole = currentEffectiveRole(account, verifiedCode);
        List<String> grantedScopes = currentGrantedScopes(effectiveRole, policy, verifiedCode.consentScopes());

        IssuedAccessToken accessToken = accessTokenIssuer.issue(new AccessTokenIssueRequest(account.userId(),
                policy.clientId(), AccountGrantTypeResolver.AUTHORIZATION_CODE, account.username(),
                effectiveRole.roleCode(), Set.copyOf(grantedScopes), policy.accessTokenTimeToLive()));
        if (accessToken == null) {
            throw new IllegalStateException("CORE AGENT access token issuer returned null");
        }
        IssuedRefreshToken refreshToken = refreshTokenSessionService.issue(new RefreshTokenIssueRequest(account.userId(),
                policy.clientId(), grantedScopes, new AccessTokenSessionReference(accessToken.jti(), accessToken.expiresAt()),
                policy.refreshTokenTimeToLive()));
        if (refreshToken == null) {
            throw new IllegalStateException("CORE AGENT refresh token issuer returned null");
        }
        return new IssuedCoreAgentAuthorizationCodeTokens(accessToken.tokenValue(), refreshToken.rawToken(), "Bearer",
                accessToken.issuedAt(), accessToken.expiresAt(), refreshToken.issuedAt(), refreshToken.expiresAt(), grantedScopes,
                verifiedCode.socketAddressChanged());
    }

    private CoreAgentRegisteredClientPolicy requiredPolicy(VerifiedCoreAgentAuthorizationCode verifiedCode) {
        CoreAgentRegisteredClientPolicy policy = policyResolver.resolve(
                CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID);
        if (policy == null || !CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID.equals(policy.clientId())
                || !verifiedCode.clientId().equals(policy.clientId())
                || !verifiedCode.registeredClientId().equals(policy.registeredClientId())) {
            throw rejected();
        }
        return policy;
    }

    private AuthorizationAccount currentAccount(VerifiedCoreAgentAuthorizationCode verifiedCode) {
        Optional<AuthorizationAccount> accountOptional = accountRepository.findById(verifiedCode.userId());
        if (accountOptional == null) {
            throw new IllegalStateException("CORE AGENT authorization account lookup returned null");
        }
        if (accountOptional.isEmpty()) {
            throw rejected();
        }
        AuthorizationAccount account = accountOptional.get();
        if (!verifiedCode.userId().equals(account.userId())) {
            throw new IllegalStateException("CORE AGENT authorization account identity is inconsistent");
        }
        if (!verifiedCode.username().equals(account.username()) || !verifiedCode.roleId().equals(account.roleId())) {
            throw rejected();
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
            throw new IllegalStateException("CORE AGENT authorization account grant configuration is invalid", exception);
        }
        if (!allowed) {
            throw rejected();
        }
    }

    private EffectiveRolePermissions currentEffectiveRole(AuthorizationAccount account,
                                                          VerifiedCoreAgentAuthorizationCode verifiedCode) {
        EffectiveRolePermissions effectiveRole = rolePermissionResolver.resolve(account.roleId());
        if (effectiveRole == null || !account.roleId().equals(effectiveRole.roleId())
                || effectiveRole.roleCode() == null || effectiveRole.roleCode().isBlank()
                || effectiveRole.roleRank() == null || effectiveRole.roleRank() <= 0) {
            throw new IllegalStateException("CORE AGENT effective role identity is inconsistent");
        }
        if (!verifiedCode.roleId().equals(effectiveRole.roleId())) {
            throw rejected();
        }
        return effectiveRole;
    }

    private List<String> currentGrantedScopes(EffectiveRolePermissions effectiveRole,
                                              CoreAgentRegisteredClientPolicy policy,
                                              List<String> cachedConsentScopes) {
        List<String> scopes = scopeResolver.resolve(effectiveRole, policy.scopes(), Set.of(), cachedConsentScopes);
        if (scopes == null) {
            throw new IllegalStateException("CORE AGENT scope resolution returned null");
        }
        if (scopes.isEmpty()) {
            throw rejected();
        }
        return scopes;
    }

    private static CoreAgentAuthorizationCodeTokenRejectedException rejected() {
        return new CoreAgentAuthorizationCodeTokenRejectedException();
    }
}
