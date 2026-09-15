package com.jacolp.system.application.authorization;

import com.jacolp.system.exception.PasswordIncorrectException;
import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.authorization.model.InternalAuthenticatedAccount;
import com.jacolp.system.application.authorization.model.InternalRegisteredClientPolicy;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.system.application.port.out.PasswordCredentialVerifier;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Password-grant authentication orchestration for an already resolved internal client policy.
 */
@Service
public class InternalPasswordAccountAuthenticator {

    private static final String PASSWORD_GRANT_TYPE = "password";

    private final AuthorizationAccountRepository authorizationAccountRepository;
    private final PasswordCredentialVerifier passwordCredentialVerifier;
    private final InternalAccountEligibilityService internalAccountEligibilityService;

    public InternalPasswordAccountAuthenticator(AuthorizationAccountRepository authorizationAccountRepository,
                                                PasswordCredentialVerifier passwordCredentialVerifier,
                                                InternalAccountEligibilityService internalAccountEligibilityService) {
        this.authorizationAccountRepository = authorizationAccountRepository;
        this.passwordCredentialVerifier = passwordCredentialVerifier;
        this.internalAccountEligibilityService = internalAccountEligibilityService;
    }

    public InternalAuthenticatedAccount authenticate(InternalRegisteredClientPolicy policy, String username,
                                                     String rawPassword) {
        if (policy == null || !PASSWORD_GRANT_TYPE.equals(policy.grantType())) {
            throw invalidPolicy();
        }
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new PasswordIncorrectException();
        }

        Optional<AuthorizationAccount> accountOptional = authorizationAccountRepository.findByUsername(username);
        if (accountOptional == null) {
            throw new IllegalStateException("Authorization account repository returned invalid metadata");
        }
        if (accountOptional.isEmpty()) {
            passwordCredentialVerifier.matches(rawPassword, null);
            throw new PasswordIncorrectException(PasswordIncorrectException.MESSAGE,
                    "Internal login account was not found");
        }

        AuthorizationAccount account = accountOptional.get();
        if (!passwordCredentialVerifier.matches(rawPassword, account.passwordHash())) {
            throw new PasswordIncorrectException(PasswordIncorrectException.MESSAGE,
                    "Internal login password does not match");
        }
        return internalAccountEligibilityService.resolve(policy, account);
    }

    private static IllegalStateException invalidPolicy() {
        return new IllegalStateException("Internal password authentication policy is invalid");
    }
}
