package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.*;
import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.authorization.model.EmailLoginCodeDeliveryRequest;
import com.jacolp.system.application.authorization.model.EmailLoginCodeIssueRateLimitRequest;
import com.jacolp.system.application.authorization.model.EmailLoginCodeIssueRequest;
import com.jacolp.system.application.authorization.model.EmailLoginCodeState;
import com.jacolp.system.application.authorization.model.InternalRegisteredClientPolicy;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.system.application.port.out.EmailLoginCodeDeliveryPort;
import com.jacolp.system.application.port.out.EmailLoginCodeGenerator;
import com.jacolp.system.application.port.out.EmailLoginCodeIssueRateLimitDecision;
import com.jacolp.system.application.port.out.EmailLoginCodeIssueRateLimiter;
import com.jacolp.system.application.port.out.EmailLoginCodeProtector;
import com.jacolp.system.application.port.out.EmailLoginCodeStateStore;
import com.jacolp.system.application.authorization.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Coordinates an email-code issuance attempt without handling HTTP responses. */
@Service
public class EmailLoginCodeIssuanceService {

    private final InternalRegisteredClientPolicyResolver clientPolicyResolver;
    private final EmailLoginBindingFingerprint fingerprint;
    private final EmailLoginCodeIssueRateLimiter rateLimiter;
    private final AuthorizationAccountRepository accountRepository;
    private final EmailLoginCodeGenerator codeGenerator;
    private final EmailLoginCodeProtector codeProtector;
    private final InternalAccountEligibilityService eligibilityService;
    private final EmailLoginCodeStateStore stateStore;
    private final EmailLoginCodeDeliveryPort deliveryPort;
    private final OAuth2EmailLoginCodeProperties properties;
    private final Clock clock;

    @Autowired
    public EmailLoginCodeIssuanceService(
            InternalRegisteredClientPolicyResolver clientPolicyResolver,
            EmailLoginBindingFingerprint fingerprint,
            EmailLoginCodeIssueRateLimiter rateLimiter,
            AuthorizationAccountRepository accountRepository,
            EmailLoginCodeGenerator codeGenerator,
            EmailLoginCodeProtector codeProtector,
            InternalAccountEligibilityService eligibilityService,
            EmailLoginCodeStateStore stateStore,
            EmailLoginCodeDeliveryPort deliveryPort,
            OAuth2EmailLoginCodeProperties properties) {
        this(clientPolicyResolver, fingerprint, rateLimiter, accountRepository, codeGenerator, codeProtector,
                eligibilityService, stateStore, deliveryPort, properties, Clock.systemUTC());
    }

    EmailLoginCodeIssuanceService(
            InternalRegisteredClientPolicyResolver clientPolicyResolver,
            EmailLoginBindingFingerprint fingerprint,
            EmailLoginCodeIssueRateLimiter rateLimiter,
            AuthorizationAccountRepository accountRepository,
            EmailLoginCodeGenerator codeGenerator,
            EmailLoginCodeProtector codeProtector,
            InternalAccountEligibilityService eligibilityService,
            EmailLoginCodeStateStore stateStore,
            EmailLoginCodeDeliveryPort deliveryPort,
            OAuth2EmailLoginCodeProperties properties,
            Clock clock) {
        this.clientPolicyResolver = Objects.requireNonNull(clientPolicyResolver, "clientPolicyResolver");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.codeGenerator = Objects.requireNonNull(codeGenerator, "codeGenerator");
        this.codeProtector = Objects.requireNonNull(codeProtector, "codeProtector");
        this.eligibilityService = Objects.requireNonNull(eligibilityService, "eligibilityService");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.deliveryPort = Objects.requireNonNull(deliveryPort, "deliveryPort");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void issue(EmailLoginCodeIssueRequest request) {
        Objects.requireNonNull(request, "request");
        InternalRegisteredClientPolicy policy = clientPolicyResolver.resolve(request.clientId(), "email-code");
        enforceSocketAllowed(policy, request.socketRemoteAddress());
        String emailFingerprint = fingerprint.email(request.email());
        String ipFingerprint = fingerprint.socketAddress(request.socketRemoteAddress());
        EmailLoginCodeIssueRateLimitDecision decision = rateLimiter.tryAcquire(new EmailLoginCodeIssueRateLimitRequest(
                emailFingerprint,
                ipFingerprint,
                properties.getIssueCooldown(),
                properties.getIssueWindow(),
                properties.getMaxIssuesPerWindow()));
        if (decision == null) {
            throw new IllegalStateException("Missing email-code issuance rate-limit decision");
        }
        if (decision == EmailLoginCodeIssueRateLimitDecision.COOLDOWN
                || decision == EmailLoginCodeIssueRateLimitDecision.WINDOW_LIMIT) {
            return;
        }
        if (decision != EmailLoginCodeIssueRateLimitDecision.ALLOWED) {
            throw new IllegalStateException("Invalid email-code issuance rate-limit decision");
        }

        String rawCode = codeGenerator.generate();
        String verifierHash = codeProtector.protect(rawCode);
        Optional<AuthorizationAccount> accountOptional = accountRepository.findByEmail(request.email());
        if (accountOptional == null) {
            throw new IllegalStateException("Email-code account lookup returned null");
        }
        if (accountOptional.isEmpty()) {
            throw new InternalAccountAuthenticationRejectedException(
                    InternalAccountAuthenticationRejectedException.Reason.ACCOUNT_NOT_FOUND);
        }
        AuthorizationAccount account = accountOptional.get();
        if (account.email() == null || !emailFingerprint.equals(fingerprint.email(account.email()))) {
            throw new InternalAccountAuthenticationRejectedException(
                    InternalAccountAuthenticationRejectedException.Reason.EMAIL_MISMATCH);
        }
        InternalAuthenticatedAccount clearedAccount = eligibilityService.resolve(policy, account);
        if (clearedAccount == null || !account.userId().equals(clearedAccount.userId())
                || clearedAccount.email() == null
                || !emailFingerprint.equals(fingerprint.email(clearedAccount.email()))) {
            throw new IllegalStateException("Email-code account identity is inconsistent");
        }

        Instant issuedAt = clock.instant();
        Instant expiresAt;
        try {
            expiresAt = issuedAt.plus(properties.getCodeTtl());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid email-code issuance expiry", exception);
        }
        EmailLoginCodeState state = new EmailLoginCodeState(policy.clientId(), clearedAccount.userId(), emailFingerprint,
                verifierHash, 0, issuedAt, expiresAt);
        stateStore.replace(state);
        try {
            deliveryPort.deliver(new EmailLoginCodeDeliveryRequest(policy.clientId(), clearedAccount.userId(),
                    clearedAccount.email(), clearedAccount.username(), rawCode, properties.getCodeTtl()));
        } catch (RuntimeException | Error deliveryFailure) {
            cleanupAfterDeliveryFailure(policy.clientId(), clearedAccount.userId(), deliveryFailure);
        }
    }

    private static void enforceSocketAllowed(InternalRegisteredClientPolicy policy, String remoteAddress) {
        ClientAllowedIpPolicy allowedIps = ClientAllowedIpPolicy.parse(policy.allowedIps());
        try {
            if (!allowedIps.allows(remoteAddress)) {
                throw new EmailLoginCodeIssuanceRejectedException();
            }
        } catch (IllegalArgumentException exception) {
            throw new EmailLoginCodeIssuanceRejectedException();
        }
    }

    private void cleanupAfterDeliveryFailure(String clientId, Long userId, Throwable deliveryFailure) {
        try {
            stateStore.delete(clientId, userId);
        } catch (RuntimeException | Error cleanupFailure) {
            cleanupFailure.addSuppressed(deliveryFailure);
            throw cleanupFailure;
        }
        rethrow(deliveryFailure);
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw (Error) failure;
    }
}
