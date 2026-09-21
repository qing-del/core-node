package com.jacolp.system.application.authorization;

import com.jacolp.system.application.port.out.CoreAgentAuthorizationCodeStore;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** Revokes the exact Redis-backed CORE AGENT authorization-code pointer. */
@Service
public final class CoreAgentAccountAuthorizationStateRevocationService
        implements AccountAuthorizationStateRevocationService {

    private static final String CORE_AGENT_CLIENT_ID = CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID;

    private final CoreAgentAuthorizationCodeStore authorizationCodeStore;

    public CoreAgentAccountAuthorizationStateRevocationService(CoreAgentAuthorizationCodeStore authorizationCodeStore) {
        this.authorizationCodeStore = Objects.requireNonNull(authorizationCodeStore, "authorizationCodeStore");
    }

    @Override
    public void revokeCurrentCoreAgentAuthorizationCode(Long userId) {
        authorizationCodeStore.invalidateCurrent(requireUserId(userId), CORE_AGENT_CLIENT_ID);
    }

    @Override
    public void revokeForSecurityFieldChange(Long userId) {
        revokeCurrentCoreAgentAuthorizationCode(userId);
    }

    private static Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        return userId;
    }
}
