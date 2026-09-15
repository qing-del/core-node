package com.jacolp.system.application.authorization;

import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.authorization.model.CoreAgentBrowserPrincipal;
import com.jacolp.system.application.authorization.model.RoleMetadata;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.system.application.port.out.PasswordCredentialVerifier;
import com.jacolp.system.application.port.out.RoleMetadataRepository;
import com.jacolp.constant.UserConstant;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Application-only username/password authentication for the CORE AGENT browser authorization flow.
 * It intentionally has no Spring Security, HTTP, session, or template dependency.
 */
@Service
public final class CoreAgentBrowserAccountAuthenticator {

    private static final Set<String> SUPPORTED_ROLE_CODES = Set.of("CREATOR", "ADMIN", "USER");

    private final AuthorizationAccountRepository authorizationAccountRepository;
    private final PasswordCredentialVerifier passwordCredentialVerifier;
    private final AccountGrantTypeResolver accountGrantTypeResolver;
    private final RoleMetadataRepository roleMetadataRepository;

    public CoreAgentBrowserAccountAuthenticator(AuthorizationAccountRepository authorizationAccountRepository,
                                                PasswordCredentialVerifier passwordCredentialVerifier,
                                                AccountGrantTypeResolver accountGrantTypeResolver,
                                                RoleMetadataRepository roleMetadataRepository) {
        this.authorizationAccountRepository = Objects.requireNonNull(authorizationAccountRepository,
                "authorizationAccountRepository");
        this.passwordCredentialVerifier = Objects.requireNonNull(passwordCredentialVerifier,
                "passwordCredentialVerifier");
        this.accountGrantTypeResolver = Objects.requireNonNull(accountGrantTypeResolver, "accountGrantTypeResolver");
        this.roleMetadataRepository = Objects.requireNonNull(roleMetadataRepository, "roleMetadataRepository");
    }

    public CoreAgentBrowserPrincipal authenticate(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw rejected();
        }
        Optional<AuthorizationAccount> accountOptional = authorizationAccountRepository.findByUsername(username);
        if (accountOptional == null) {
            throw new IllegalStateException("CORE AGENT authorization account repository returned null");
        }
        if (accountOptional.isEmpty()) {
            passwordCredentialVerifier.matches(rawPassword, null);
            throw rejected();
        }

        AuthorizationAccount account = accountOptional.get();
        if (!passwordCredentialVerifier.matches(rawPassword, account.passwordHash())) {
            throw rejected();
        }
        if (account.status() != UserConstant.ACTIVE_STATUS) {
            throw rejected();
        }
        verifyAuthorizationCodeGrant(account);
        RoleMetadata role = requiredRole(account.roleId());
        return new CoreAgentBrowserPrincipal(account.userId(), account.username(), account.roleId(), role.roleCode(),
                role.rank());
    }

    private void verifyAuthorizationCodeGrant(AuthorizationAccount account) {
        final boolean allowed;
        try {
            allowed = accountGrantTypeResolver.allows(AccountGrantTypeResolver.AUTHORIZATION_CODE,
                    account.extraGrantTypes());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("CORE AGENT account grant configuration is invalid", exception);
        }
        if (!allowed) {
            throw rejected();
        }
    }

    private RoleMetadata requiredRole(Long expectedRoleId) {
        Optional<RoleMetadata> roleOptional = roleMetadataRepository.findById(expectedRoleId);
        if (roleOptional == null || roleOptional.isEmpty()) {
            throw new IllegalStateException("CORE AGENT role metadata is missing");
        }
        RoleMetadata role = roleOptional.get();
        if (!expectedRoleId.equals(role.id()) || role.rank() == null || role.rank() <= 0
                || !SUPPORTED_ROLE_CODES.contains(role.roleCode())) {
            throw new IllegalStateException("CORE AGENT role metadata is invalid");
        }
        return role;
    }

    private static CoreAgentBrowserAuthenticationRejectedException rejected() {
        return new CoreAgentBrowserAuthenticationRejectedException();
    }
}
