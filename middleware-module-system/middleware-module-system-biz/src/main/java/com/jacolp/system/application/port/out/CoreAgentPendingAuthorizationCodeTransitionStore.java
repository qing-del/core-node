package com.jacolp.system.application.port.out;

import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationCodeState;
import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationState;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationPendingHandle;

/**
 * Atomic boundary that consumes one pending browser authorization and persists its generated
 * authorization code.
 *
 * <p>Java supplies the secure-random raw code and pre-encoded state. The persistence adapter must
 * only validate key/argument/pending binding and atomically delete pending state, write the code
 * Hash and TTL, and point the code's user/client pair at it. A replaced pointer never deletes its
 * old code Hash; exchange rejects it by pointer mismatch and Redis expires it naturally.</p>
 */
public interface CoreAgentPendingAuthorizationCodeTransitionStore {

    boolean consumePendingAndStoreCode(
            IssuedCoreAgentAuthorizationPendingHandle pendingHandle,
            CoreAgentPendingAuthorizationState expectedPending,
            CoreAgentAuthorizationCodeState codeState);
}
