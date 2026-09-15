package com.jacolp.system.application.port.out;

import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationState;
import com.jacolp.system.application.authorization.model.IssuedCoreAgentAuthorizationPendingHandle;

import java.util.Optional;

/**
 * Persistence boundary for a short-lived complete browser authorization request awaiting code
 * conversion.
 *
 * <p>The opaque handle is the Redis key suffix and is deliberately absent from the Hash. The
 * later atomic pending-to-code transition owns consumption; this port only creates, reads, and
 * deletes abandoned pending state.</p>
 */
public interface CoreAgentPendingAuthorizationStore {

    void save(IssuedCoreAgentAuthorizationPendingHandle handle, CoreAgentPendingAuthorizationState state);

    Optional<CoreAgentPendingAuthorizationState> find(String rawHandle);

    void delete(String rawHandle);
}
