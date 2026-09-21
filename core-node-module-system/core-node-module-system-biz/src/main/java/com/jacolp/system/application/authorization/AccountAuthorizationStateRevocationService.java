package com.jacolp.system.application.authorization;

/**
 * Application boundary for revoking authorization state after a security-relevant account change.
 *
 * <p>The first release revokes only the exact current CORE AGENT authorization-code pointer. Future
 * authorization-state types must be added here rather than coupled directly to account write services.</p>
 */
public interface AccountAuthorizationStateRevocationService {

    /** Revokes the current CORE AGENT authorization code for one account; missing state is successful. */
    void revokeCurrentCoreAgentAuthorizationCode(Long userId);

    /** Coordinates all authorization-state revocations required after one security-field change. */
    void revokeForSecurityFieldChange(Long userId);
}
