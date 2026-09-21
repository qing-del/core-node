package com.jacolp.system.application.authorization;

import com.jacolp.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.system.application.port.out.AuthorizationAccountRepository;
import com.jacolp.system.application.port.out.CoreAgentAuthorizationCodeStore;
import com.jacolp.constant.UserConstant;
import com.jacolp.system.application.authorization.model.AuthorizationAccount;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationAccountSnapshot;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationCodeExchangeRequest;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationCodeState;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.VerifiedCoreAgentAuthorizationCode;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/** Verifies and atomically consumes a CORE AGENT authorization code before token issuance. */
@Service
public class CoreAgentAuthorizationCodeExchangeService {

    private final CoreAgentRegisteredClientPolicyResolver policyResolver;
    private final CoreAgentAuthorizationCodeStore authorizationCodeStore;
    private final AuthorizationAccountRepository accountRepository;
    private final AccountGrantTypeResolver accountGrantTypeResolver;

    public CoreAgentAuthorizationCodeExchangeService(
            CoreAgentRegisteredClientPolicyResolver policyResolver,
            CoreAgentAuthorizationCodeStore authorizationCodeStore,
            AuthorizationAccountRepository accountRepository,
            AccountGrantTypeResolver accountGrantTypeResolver) {
        this.policyResolver = Objects.requireNonNull(policyResolver, "policyResolver");
        this.authorizationCodeStore = Objects.requireNonNull(authorizationCodeStore, "authorizationCodeStore");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.accountGrantTypeResolver = Objects.requireNonNull(accountGrantTypeResolver, "accountGrantTypeResolver");
    }

    /**
     * Returns only a verified identity/consent snapshot. The eventual token provider must calculate
     * final scopes again against current role permissions and the active client policy before signing.
     */
    public VerifiedCoreAgentAuthorizationCode exchange(CoreAgentAuthorizationCodeExchangeRequest request) {
        Objects.requireNonNull(request, "request");
        CoreAgentRegisteredClientPolicy policy = policyResolver.resolve(
                CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID);
        if (!policy.clientId().equals(request.clientId()) || !policy.redirectUri().equals(request.redirectUri())) {
            throw rejected();
        }
        Optional<CoreAgentAuthorizationCodeState> stateOptional = authorizationCodeStore.findByCode(request.rawCode());
        if (stateOptional == null) {
            throw new IllegalStateException("CORE AGENT authorization-code lookup returned null");
        }
        if (stateOptional.isEmpty()) {
            throw rejected();
        }
        CoreAgentAuthorizationCodeState state = stateOptional.get();
        if (!request.rawCode().equals(state.rawCode())) {
            throw new IllegalStateException("CORE AGENT authorization-code state identity is inconsistent");
        }
        if (!policy.clientId().equals(state.clientId()) || !policy.redirectUri().equals(state.redirectUri())) {
            throw rejected();
        }
        if (!pkceMatches(request.codeVerifier(), state.codeChallenge())) {
            throw rejected();
        }

        AuthorizationAccount account = currentAccount(state.accountSnapshot().userId());
        verifyAuthorizationCodeGrant(account);
        CoreAgentAuthorizationAccountSnapshot currentSnapshot = snapshot(account);
        if (!state.accountSnapshot().equals(currentSnapshot)) {
            authorizationCodeStore.consume(request.rawCode(), account.userId(), policy.clientId());
            throw rejected();
        }
        if (state.scopes().isEmpty()) {
            throw new IllegalStateException("CORE AGENT authorization-code state has no scopes");
        }
        if (!authorizationCodeStore.consume(request.rawCode(), account.userId(), policy.clientId())) {
            throw rejected();
        }
        return new VerifiedCoreAgentAuthorizationCode(policy.registeredClientId(), policy.clientId(), account.userId(),
                account.username(), account.roleId(), state.scopes(), AccountGrantTypeResolver.AUTHORIZATION_CODE,
                !state.originalSocketAddress().equals(request.socketRemoteAddress()));
    }

    private AuthorizationAccount currentAccount(Long expectedUserId) {
        Optional<AuthorizationAccount> accountOptional = accountRepository.findById(expectedUserId);
        if (accountOptional == null) {
            throw new IllegalStateException("CORE AGENT authorization account lookup returned null");
        }
        if (accountOptional.isEmpty()) {
            throw rejected();
        }
        AuthorizationAccount account = accountOptional.get();
        if (!expectedUserId.equals(account.userId())) {
            throw new IllegalStateException("CORE AGENT authorization account identity is inconsistent");
        }
        return account;
    }

    private void verifyAuthorizationCodeGrant(AuthorizationAccount account) {
        if (account.status() != UserConstant.ACTIVE_STATUS) {
            throw rejected();
        }
        final boolean allowed;
        try {
            allowed = accountGrantTypeResolver.allows(AccountGrantTypeResolver.AUTHORIZATION_CODE,
                    account.extraGrantTypes());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("CORE AGENT authorization account grant configuration is invalid");
        }
        if (!allowed) {
            throw rejected();
        }
    }

    private static CoreAgentAuthorizationAccountSnapshot snapshot(AuthorizationAccount account) {
        return new CoreAgentAuthorizationAccountSnapshot(account.userId(), account.username(), account.roleId(),
                account.passwordHash(), account.email(), account.extraGrantTypes(), account.status());
    }

    private static boolean pkceMatches(String codeVerifier, String expectedChallenge) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            String actualChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return MessageDigest.isEqual(actualChallenge.getBytes(StandardCharsets.US_ASCII),
                    expectedChallenge.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static CoreAgentAuthorizationCodeExchangeRejectedException rejected() {
        return new CoreAgentAuthorizationCodeExchangeRejectedException();
    }
}
