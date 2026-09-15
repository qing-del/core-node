package com.jacolp.system.application.authorization;

import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.system.application.port.out.EmailLoginCodeFailureDecision;
import com.jacolp.system.application.port.out.EmailLoginCodeProtector;
import com.jacolp.system.application.port.out.EmailLoginCodeStateStore;
import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.authorization.model.EmailLoginCodeAuthenticationRequest;
import com.jacolp.system.application.authorization.model.EmailLoginCodeState;
import com.jacolp.system.application.authorization.model.InternalAuthenticatedAccount;
import com.jacolp.system.application.authorization.model.InternalRegisteredClientPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Authenticates and atomically consumes one protected email-login code. */
@Service
public class EmailLoginCodeAuthenticator {

    private final AuthorizationAccountRepository accountRepository;
    private final EmailLoginCodeProtector protector;
    private final EmailLoginCodeStateStore stateStore;
    private final InternalAccountEligibilityService eligibilityService;
    private final EmailLoginBindingFingerprint fingerprint;
    private final OAuth2EmailLoginCodeProperties properties;
    private final Clock clock;

    @Autowired
    public EmailLoginCodeAuthenticator(AuthorizationAccountRepository accountRepository, EmailLoginCodeProtector protector,
                                       EmailLoginCodeStateStore stateStore, InternalAccountEligibilityService eligibilityService,
                                       EmailLoginBindingFingerprint fingerprint, OAuth2EmailLoginCodeProperties properties) {
        this(accountRepository, protector, stateStore, eligibilityService, fingerprint, properties, Clock.systemUTC());
    }

    EmailLoginCodeAuthenticator(AuthorizationAccountRepository accountRepository, EmailLoginCodeProtector protector,
                                EmailLoginCodeStateStore stateStore, InternalAccountEligibilityService eligibilityService,
                                EmailLoginBindingFingerprint fingerprint, OAuth2EmailLoginCodeProperties properties, Clock clock) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.protector = Objects.requireNonNull(protector, "protector");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.eligibilityService = Objects.requireNonNull(eligibilityService, "eligibilityService");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public InternalAuthenticatedAccount authenticate(InternalRegisteredClientPolicy policy,
                                                     EmailLoginCodeAuthenticationRequest request) {
        validatePolicy(policy);
        Objects.requireNonNull(request, "request");
        Optional<AuthorizationAccount> accountOptional = accountRepository.findByEmail(request.email());
        if (accountOptional == null) {
            throw new IllegalStateException("Email-code account lookup returned null");
        }
        if (accountOptional.isEmpty()) {
            dummyReject(request.rawCode());
        }
        AuthorizationAccount account = accountOptional.get();
        String requestedFingerprint = fingerprint.email(request.email());
        if (account.email() == null || !requestedFingerprint.equals(fingerprint.email(account.email()))) {
            dummyReject(request.rawCode());
        }
        Optional<EmailLoginCodeState> stateOptional = stateStore.find(policy.clientId(), account.userId());
        if (stateOptional == null) {
            throw new IllegalStateException("Email-code state lookup returned null");
        }
        if (stateOptional.isEmpty()) {
            dummyReject(request.rawCode());
        }
        EmailLoginCodeState state = stateOptional.get();
        if (!requestedFingerprint.equals(state.emailFingerprint())) {
            dummyReject(request.rawCode());
        }
        boolean codeMatches = protector.matches(request.rawCode(), state.verifierHash());
        Instant now = clock.instant();
        if (state.issuedAt().isAfter(now)) {
            stateStore.delete(policy.clientId(), account.userId());
            throw new IllegalStateException("Email-code state is issued in the future");
        }
        if (!state.expiresAt().isAfter(now)) {
            stateStore.delete(policy.clientId(), account.userId());
            throw rejected();
        }
        if (!codeMatches) {
            EmailLoginCodeFailureDecision decision = stateStore.recordFailure(
                    policy.clientId(), account.userId(), state.verifierHash(), properties.getMaxFailedAttempts());
            if (decision == null) {
                throw new IllegalStateException("Missing email-code failure decision");
            }
            if (decision == EmailLoginCodeFailureDecision.RECORDED
                    || decision == EmailLoginCodeFailureDecision.INVALIDATED
                    || decision == EmailLoginCodeFailureDecision.STALE) {
                throw rejected();
            }
            throw new IllegalStateException("Invalid email-code failure decision");
        }
        if (!stateStore.consume(policy.clientId(), account.userId(), state.verifierHash())) {
            throw rejected();
        }
        InternalAuthenticatedAccount cleared = eligibilityService.resolve(policy, account);
        if (cleared == null || !account.userId().equals(cleared.userId()) || cleared.email() == null
                || !requestedFingerprint.equals(fingerprint.email(cleared.email()))) {
            throw new IllegalStateException("Email-code account identity is inconsistent");
        }
        return cleared;
    }

    private void dummyReject(String rawCode) {
        protector.matches(rawCode, null);
        throw rejected();
    }

    private static void validatePolicy(InternalRegisteredClientPolicy policy) {
        if (policy == null || !(("user".equals(policy.clientId()) || "admin".equals(policy.clientId()))
                && "email-code".equals(policy.grantType()))) {
            throw new IllegalStateException("Invalid email-code authentication policy");
        }
    }

    private static InternalAccountAuthenticationRejectedException rejected() {
        return new InternalAccountAuthenticationRejectedException(
                InternalAccountAuthenticationRejectedException.Reason.EMAIL_CODE_INVALID);
    }
}
