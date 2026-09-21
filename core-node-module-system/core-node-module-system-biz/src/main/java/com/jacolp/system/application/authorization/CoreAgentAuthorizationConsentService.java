package com.jacolp.system.application.authorization;

import com.jacolp.system.application.port.out.CoreAgentAuthorizationConsentStore;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationConsentDecision;
import com.jacolp.system.application.authorization.model.CoreAgentConsentScopeOptions;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.PermissionScopePattern;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Application-only orchestration of CORE AGENT browser consent decisions and persistence.
 *
 * <p>Stored patterns are never expanded or minimized. Cancelling an option removes only the
 * exact current candidate pattern from stored consent; it cannot split or manufacture wildcard
 * patterns. Token issuance must still intersect consent with the current permission catalogue.</p>
 */
@Service
public final class CoreAgentAuthorizationConsentService {

    private final CoreAgentConsentScopeService consentScopeService;
    private final CoreAgentAuthorizationConsentStore authorizationConsentStore;

    public CoreAgentAuthorizationConsentService(CoreAgentConsentScopeService consentScopeService,
                                                CoreAgentAuthorizationConsentStore authorizationConsentStore) {
        this.consentScopeService = Objects.requireNonNull(consentScopeService, "consentScopeService");
        this.authorizationConsentStore = Objects.requireNonNull(authorizationConsentStore, "authorizationConsentStore");
    }

    public CoreAgentAuthorizationConsentDecision prepare(Long userId,
                                                         EffectiveRolePermissions effectiveRolePermissions,
                                                         CoreAgentRegisteredClientPolicy policy,
                                                         Collection<String> requestedScopes) {
        String principalName = principalName(userId);
        CoreAgentRegisteredClientPolicy safePolicy = requirePolicy(policy);
        Set<String> existingScopes = loadExisting(safePolicy.registeredClientId(), principalName);
        CoreAgentConsentScopeOptions options = consentScopeService.options(effectiveRolePermissions, safePolicy,
                requestedScopes, existingScopes);
        boolean consentRequired = !coversEveryCandidate(existingScopes, options.candidateScopes());
        return new CoreAgentAuthorizationConsentDecision(options, consentRequired,
                consentRequired ? List.of() : options.candidateScopes());
    }

    /**
     * Re-reads stored consent and recomputes scope options before persisting this submission, so a
     * prior page render never decides the final authorization result.
     */
    public List<String> confirm(Long userId,
                                EffectiveRolePermissions effectiveRolePermissions,
                                CoreAgentRegisteredClientPolicy policy,
                                Collection<String> requestedScopes,
                                Collection<String> submittedOptionalScopes) {
        String principalName = principalName(userId);
        CoreAgentRegisteredClientPolicy safePolicy = requirePolicy(policy);
        Set<String> existingScopes = loadExisting(safePolicy.registeredClientId(), principalName);
        CoreAgentConsentScopeOptions options = consentScopeService.options(effectiveRolePermissions, safePolicy,
                requestedScopes, existingScopes);
        final List<String> finalScopes;
        try {
            finalScopes = consentScopeService.confirm(effectiveRolePermissions, safePolicy, requestedScopes,
                    existingScopes, submittedOptionalScopes);
        } catch (IllegalArgumentException exception) {
            throw rejected();
        }
        if (finalScopes == null || finalScopes.isEmpty()) {
            throw new IllegalStateException("CORE AGENT consent scope confirmation returned no scopes");
        }

        Set<String> persisted = new LinkedHashSet<>();
        for (String existingScope : existingScopes) {
            if (!options.candidateScopes().contains(existingScope)) {
                persisted.add(existingScope);
            }
        }
        persisted.addAll(finalScopes);
        if (persisted.isEmpty()) {
            throw new IllegalStateException("CORE AGENT persisted consent cannot be empty");
        }
        List<String> sortedPersisted = persisted.stream().sorted().toList();
        authorizationConsentStore.saveScopes(safePolicy.registeredClientId(), principalName, sortedPersisted);
        return List.copyOf(finalScopes);
    }

    private Set<String> loadExisting(String registeredClientId, String principalName) {
        Optional<Set<String>> existingOptional = authorizationConsentStore.findScopes(registeredClientId, principalName);
        if (existingOptional == null) {
            throw new IllegalStateException("CORE AGENT consent lookup returned null");
        }
        if (existingOptional.isEmpty()) {
            return Set.of();
        }
        Set<String> rawExisting = existingOptional.get();
        if (rawExisting == null || rawExisting.isEmpty()) {
            throw new IllegalStateException("CORE AGENT stored consent is invalid");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String scope : rawExisting) {
            String canonical = canonicalStoredScope(scope);
            if (!normalized.add(canonical)) {
                throw new IllegalStateException("CORE AGENT stored consent contains duplicate scopes");
            }
        }
        List<String> sorted = new ArrayList<>(normalized);
        sorted.sort(String::compareTo);
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }

    private static boolean coversEveryCandidate(Collection<String> existingScopes, Collection<String> candidates) {
        for (String candidate : candidates) {
            PermissionScopePattern candidatePattern = PermissionScopePattern.parse(candidate);
            boolean covered = existingScopes.stream()
                    .map(PermissionScopePattern::parse)
                    .anyMatch(existing -> existing.meet(candidatePattern).filter(candidatePattern::equals).isPresent());
            if (!covered) {
                return false;
            }
        }
        return true;
    }

    private static String canonicalStoredScope(String scope) {
        if (scope == null || scope.isBlank() || !scope.equals(scope.trim())) {
            throw new IllegalStateException("CORE AGENT stored consent contains an invalid scope");
        }
        try {
            String canonical = PermissionScopePattern.parse(scope).asScope();
            if (!scope.equals(canonical)) {
                throw new IllegalStateException("CORE AGENT stored consent contains a non-canonical scope");
            }
            return canonical;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("CORE AGENT stored consent contains an invalid scope", exception);
        }
    }

    private static CoreAgentRegisteredClientPolicy requirePolicy(CoreAgentRegisteredClientPolicy policy) {
        if (policy == null || policy.registeredClientId() == null || policy.registeredClientId().isBlank()) {
            throw new IllegalArgumentException("CORE AGENT registered client policy is required");
        }
        return policy;
    }

    private static String principalName(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("CORE AGENT user id must be positive");
        }
        return Long.toString(userId);
    }

    private static CoreAgentAuthorizationConsentRejectedException rejected() {
        return new CoreAgentAuthorizationConsentRejectedException();
    }
}
