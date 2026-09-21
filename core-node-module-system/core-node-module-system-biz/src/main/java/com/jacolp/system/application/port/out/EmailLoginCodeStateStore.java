package com.jacolp.system.application.port.out;

import com.jacolp.system.application.authorization.model.EmailLoginCodeState;

import java.util.Optional;

/** Read/replace/consume/failure/delete port for protected email-code challenge state. */
public interface EmailLoginCodeStateStore {

    Optional<EmailLoginCodeState> find(String clientId, Long userId);

    void replace(EmailLoginCodeState state);

    boolean consume(String clientId, Long userId, String expectedVerifierHash);

    EmailLoginCodeFailureDecision recordFailure(
            String clientId,
            Long userId,
            String expectedVerifierHash,
            Integer maxFailedAttempts);

    void delete(String clientId, Long userId);
}
