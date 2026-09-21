package com.jacolp.system.application.authorization;

import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.common.security.oauth2.token.AccessTokenIssueRequest;
import com.jacolp.common.security.oauth2.token.AccessTokenSessionReference;
import com.jacolp.common.security.oauth2.token.IssuedAccessToken;
import com.jacolp.common.security.oauth2.token.IssuedRefreshToken;
import com.jacolp.common.security.oauth2.token.OAuth2RefreshTokenSessionService;
import com.jacolp.common.security.oauth2.token.RefreshTokenIssueRequest;
import com.jacolp.common.security.oauth2.token.Rs256AccessTokenIssuer;
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.EmailLoginCodeAuthenticationRequest;
import com.jacolp.system.application.authorization.model.InternalAuthenticatedAccount;
import com.jacolp.system.application.authorization.model.InternalIssuedTokens;
import com.jacolp.system.application.authorization.model.InternalLoginRequest;
import com.jacolp.system.application.authorization.model.InternalRefreshTokenRequest;
import com.jacolp.system.application.authorization.model.InternalRegisteredClientPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Orchestrates one password or email-code login and issues the two internal tokens. */
@Service
public class InternalLoginService {

    private final InternalRegisteredClientPolicyResolver policyResolver;
    private final InternalPasswordAccountAuthenticator passwordAuthenticator;
    private final EmailLoginCodeAuthenticator emailCodeAuthenticator;
    private final EffectiveRolePermissionResolver rolePermissionResolver;
    private final OAuth2ScopeResolver scopeResolver;
    private final Rs256AccessTokenIssuer accessTokenIssuer;
    private final OAuth2RefreshTokenSessionService refreshTokenSessionService;
    private final InternalRefreshTokenService refreshTokenService;

    @Autowired
    public InternalLoginService(
            InternalRegisteredClientPolicyResolver policyResolver,
            InternalPasswordAccountAuthenticator passwordAuthenticator,
            EmailLoginCodeAuthenticator emailCodeAuthenticator,
            EffectiveRolePermissionResolver rolePermissionResolver,
            OAuth2ScopeResolver scopeResolver,
            Rs256AccessTokenIssuer accessTokenIssuer,
            OAuth2RefreshTokenSessionService refreshTokenSessionService,
            InternalRefreshTokenService refreshTokenService) {
        this.policyResolver = Objects.requireNonNull(policyResolver, "policyResolver");
        this.passwordAuthenticator = Objects.requireNonNull(passwordAuthenticator, "passwordAuthenticator");
        this.emailCodeAuthenticator = Objects.requireNonNull(emailCodeAuthenticator, "emailCodeAuthenticator");
        this.rolePermissionResolver = Objects.requireNonNull(rolePermissionResolver, "rolePermissionResolver");
        this.scopeResolver = Objects.requireNonNull(scopeResolver, "scopeResolver");
        this.accessTokenIssuer = Objects.requireNonNull(accessTokenIssuer, "accessTokenIssuer");
        this.refreshTokenSessionService = Objects.requireNonNull(refreshTokenSessionService,
                "refreshTokenSessionService");
        this.refreshTokenService = Objects.requireNonNull(refreshTokenService, "refreshTokenService");
    }

    public InternalIssuedTokens login(InternalLoginRequest request) {
        Objects.requireNonNull(request, "request");
        if (AccountGrantTypeResolver.REFRESH_TOKEN.equals(request.grantType())) {
            return refreshTokenService.refresh(new InternalRefreshTokenRequest(request.clientId(), request.rawRefreshToken(),
                    request.requestedScopes() == null ? null : new ArrayList<>(request.requestedScopes()),
                    request.socketRemoteAddress()));
        }
        InternalRegisteredClientPolicy policy = policyResolver.resolve(request.clientId(), request.grantType());
        validateResolvedPolicy(policy, request);
        enforceSocketAllowed(policy, request.socketRemoteAddress());

        InternalAuthenticatedAccount account = authenticate(policy, request);
        validateAuthenticatedAccount(account);
        EffectiveRolePermissions effectiveRole = rolePermissionResolver.resolve(account.roleId());
        validateEffectiveRole(effectiveRole, account);
        List<String> grantedScopes = scopeResolver.resolve(effectiveRole, policy.scopes(),
                policy.autoApproveScopes(), request.requestedScopes());
        if (grantedScopes == null) {
            throw new IllegalStateException("Internal login scope resolution returned null");
        }
        if (grantedScopes.isEmpty()) {
            throw new InternalAccountAuthenticationRejectedException(
                    InternalAccountAuthenticationRejectedException.Reason.NO_EFFECTIVE_PERMISSION);
        }

        IssuedAccessToken accessToken = accessTokenIssuer.issue(new AccessTokenIssueRequest(
                account.userId(), policy.clientId(), policy.grantType(), account.username(), account.roleCode(),
                Set.copyOf(grantedScopes), policy.accessTokenTimeToLive()));
        if (accessToken == null) {
            throw new IllegalStateException("Internal login access token issuance returned null");
        }
        IssuedRefreshToken refreshToken = refreshTokenSessionService.issue(new RefreshTokenIssueRequest(
                account.userId(), policy.clientId(), grantedScopes,
                new AccessTokenSessionReference(accessToken.jti(), accessToken.expiresAt()),
                policy.refreshTokenTimeToLive()));
        if (refreshToken == null) {
            throw new IllegalStateException("Internal login refresh token issuance returned null");
        }
        return new InternalIssuedTokens(accessToken.tokenValue(), refreshToken.rawToken(), "Bearer",
                accessToken.issuedAt(), accessToken.expiresAt(), refreshToken.expiresAt(), grantedScopes);
    }

    private InternalAuthenticatedAccount authenticate(InternalRegisteredClientPolicy policy,
                                                      InternalLoginRequest request) {
        return switch (request.grantType()) {
            case "password" -> passwordAuthenticator.authenticate(policy, request.username(), request.rawPassword());
            case "email-code" -> emailCodeAuthenticator.authenticate(policy,
                    new EmailLoginCodeAuthenticationRequest(request.email(), request.rawEmailCode()));
            default -> throw new InternalAccountAuthenticationRejectedException(
                    InternalAccountAuthenticationRejectedException.Reason.UNSUPPORTED_GRANT_TYPE);
        };
    }

    private static void enforceSocketAllowed(InternalRegisteredClientPolicy policy, String remoteAddress) {
        final ClientAllowedIpPolicy allowedIps;
        try {
            allowedIps = ClientAllowedIpPolicy.parse(policy.allowedIps());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Internal login allowed IP policy is invalid", exception);
        }
        try {
            if (!allowedIps.allows(remoteAddress)) {
                throw new InternalAccountAuthenticationRejectedException(
                        InternalAccountAuthenticationRejectedException.Reason.IP_NOT_ALLOWED);
            }
        } catch (IllegalArgumentException exception) {
            throw new InternalAccountAuthenticationRejectedException(
                    InternalAccountAuthenticationRejectedException.Reason.IP_NOT_ALLOWED);
        }
    }

    private static void validateResolvedPolicy(InternalRegisteredClientPolicy policy, InternalLoginRequest request) {
        if (policy == null || !request.clientId().equals(policy.clientId())
                || !request.grantType().equals(policy.grantType())) {
            throw new IllegalStateException("Internal login policy is invalid");
        }
    }

    private static void validateAuthenticatedAccount(InternalAuthenticatedAccount account) {
        if (account == null || account.userId() == null || account.userId() <= 0 || account.roleId() == null
                || account.roleId() <= 0 || account.rank() == null || account.rank() <= 0
                || account.username() == null || account.username().isBlank()
                || account.roleCode() == null || account.roleCode().isBlank()) {
            throw new IllegalStateException("Internal login account identity is invalid");
        }
    }

    private static void validateEffectiveRole(EffectiveRolePermissions effectiveRole,
                                              InternalAuthenticatedAccount account) {
        if (effectiveRole == null || !account.roleId().equals(effectiveRole.roleId())
                || !account.roleCode().equals(effectiveRole.roleCode())
                || !account.rank().equals(effectiveRole.roleRank())) {
            throw new IllegalStateException("Internal login role metadata is inconsistent");
        }
    }
}
