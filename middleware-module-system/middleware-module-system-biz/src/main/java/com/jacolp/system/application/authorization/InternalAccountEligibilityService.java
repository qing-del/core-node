package com.jacolp.system.application.authorization;

import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.authorization.model.InternalAuthenticatedAccount;
import com.jacolp.system.application.authorization.model.InternalRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.RoleMetadata;
import com.jacolp.system.application.port.out.RoleMetadataRepository;
import com.jacolp.constant.UserConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

/**
 * Clears an already-loaded account for a fixed internal client without inspecting credentials or issuing tokens.
 */
@Service
@Slf4j
public class InternalAccountEligibilityService {

    private static final Set<String> INTERNAL_CLIENT_IDS = Set.of("user", "admin");
    private static final Set<String> LOGIN_GRANT_TYPES = Set.of("password", "email-code");
    private static final Set<String> MANAGEMENT_ROLE_CODES = Set.of("ADMIN", "CREATOR");

    private final AccountGrantTypeResolver accountGrantTypeResolver;
    private final RoleMetadataRepository roleMetadataRepository;

    public InternalAccountEligibilityService(AccountGrantTypeResolver accountGrantTypeResolver,
                                             RoleMetadataRepository roleMetadataRepository) {
        this.accountGrantTypeResolver = accountGrantTypeResolver;
        this.roleMetadataRepository = roleMetadataRepository;
    }

    public InternalAuthenticatedAccount resolve(InternalRegisteredClientPolicy policy, AuthorizationAccount account) {
        validatePolicy(policy);
        if (account == null) {
            throw rejected();
        }
        if (account.status() != UserConstant.ACTIVE_STATUS) {
            throw accountStatusRejected(account.status());
        }
        final boolean allowed;
        try {
            allowed = accountGrantTypeResolver.allows(policy.grantType(), account.extraGrantTypes());
        } catch (IllegalArgumentException exception) {
            throw invalidConfiguration();
        }
        if (!allowed) {
            throw rejected(InternalAccountAuthenticationRejectedException.Reason.GRANT_TYPE_NOT_ALLOWED);
        }

        RoleMetadata role = loadRole(account.roleId());
        if (!isRoleAllowedForClient(policy.clientId(), role.roleCode())) {
            throw rejected(InternalAccountAuthenticationRejectedException.Reason.ROLE_NOT_ALLOWED);
        }
        return new InternalAuthenticatedAccount(account.userId(), account.username(), account.email(), account.roleId(),
                role.roleCode(), role.rank());
    }

    private static void validatePolicy(InternalRegisteredClientPolicy policy) {
        if (policy == null || !INTERNAL_CLIENT_IDS.contains(policy.clientId())
                || !LOGIN_GRANT_TYPES.contains(policy.grantType())) {
            throw invalidConfiguration();
        }
    }

    private RoleMetadata loadRole(Long roleId) {
        Optional<RoleMetadata> roleOptional = roleMetadataRepository.findById(roleId);
        if (roleOptional == null || roleOptional.isEmpty()) {
            throw invalidConfiguration();
        }
        RoleMetadata role = roleOptional.get();
        if (!roleId.equals(role.id()) || role.roleCode() == null || role.roleCode().isBlank()
                || role.rank() == null || role.rank() <= 0) {
            throw invalidConfiguration();
        }
        return role;
    }

    /**
     * 校验角色是否可以使用当前客户端进行登录
     * <p>首发允许 ADMIN 和 CREATOR 使用 admin 和 user 客户端登录</p>
     * <p>而 USER 仅仅只可以使用 user 客户端登录</p>
     * @param clientId 客户端 ID
     * @param roleCode 角色 Code
     * @return 是否允许登录
     */
    static boolean isRoleAllowedForClient(String clientId, String roleCode) {
        if (clientId == null || roleCode == null) {
            log.warn("the null clientId or roleCode, please inspecting!");
            return false;
        }

        // ADMIN and CREATOR may authenticate through either internal client.
        if (MANAGEMENT_ROLE_CODES.contains(roleCode)) {
            return true;
        }

        // USER remains limited to the user client.
        return ("user".equals(clientId) && "USER".equals(roleCode));
    }

    private static InternalAccountAuthenticationRejectedException accountStatusRejected(Integer status) {
        if (status != null && status == UserConstant.UNACTIVE_STATUS) {
            return rejected(InternalAccountAuthenticationRejectedException.Reason.ACCOUNT_NOT_ACTIVATED);
        }
        return rejected(InternalAccountAuthenticationRejectedException.Reason.ACCOUNT_DISABLED);
    }

    private static InternalAccountAuthenticationRejectedException rejected() {
        return new InternalAccountAuthenticationRejectedException();
    }

    private static InternalAccountAuthenticationRejectedException rejected(
            InternalAccountAuthenticationRejectedException.Reason reason) {
        return new InternalAccountAuthenticationRejectedException(reason);
    }

    private static IllegalStateException invalidConfiguration() {
        return new IllegalStateException("Internal account authorization metadata is invalid");
    }
}
