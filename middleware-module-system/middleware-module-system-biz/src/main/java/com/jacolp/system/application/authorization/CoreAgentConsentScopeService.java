package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.CoreAgentConsentScopeOptions;
import com.jacolp.system.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.PermissionScopePattern;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes and confirms the scopes of a single CORE AGENT browser consent submission.
 *
 * <p>Prior consent is merely a page preselection. It cannot bypass a fresh submission, and
 * {@code auto_approve} scopes are recomputed server-side on every confirmation rather than read
 * from a hidden browser field.</p>
 */
@Service
public final class CoreAgentConsentScopeService {

    private final OAuth2ScopeResolver scopeResolver;

    public CoreAgentConsentScopeService(OAuth2ScopeResolver scopeResolver) {
        if (scopeResolver == null) {
            throw new IllegalArgumentException("OAuth2 scope resolver is required");
        }
        this.scopeResolver = scopeResolver;
    }

    /**
     * Computes candidate, server-mandatory, optional, and prior-consent preselected patterns.
     * A {@code null} request represents an omitted OAuth {@code scope} parameter; an empty
     * collection is an explicit request for no scopes and is rejected when it yields no candidate.
     */
    public CoreAgentConsentScopeOptions options(EffectiveRolePermissions effectiveRolePermissions,
                                                CoreAgentRegisteredClientPolicy policy,
                                                Collection<String> requestedScopes,
                                                Collection<String> existingConsentScopes) {
        requireInputs(effectiveRolePermissions, policy);
        List<String> clientScopes = canonicalDistinct(policy.scopes(), "client scopes");
        List<String> autoApproveScopes = canonicalDistinct(policy.autoApproveScopes(), "auto-approve scopes");
        List<String> canonicalRequested = requestedScopes == null
                ? null : canonicalDistinct(requestedScopes, "requested scopes");

        List<String> candidates = canonicalRequested == null
                ? withoutSuper(scopeResolver.resolve(effectiveRolePermissions, clientScopes, List.of(), clientScopes))
                : scopeResolver.resolve(effectiveRolePermissions, clientScopes, autoApproveScopes, canonicalRequested);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No CORE AGENT consent scopes are available");
        }

        List<String> mandatory = meetWithoutSuper(candidates, autoApproveScopes);
        if (canonicalRequested == null && mandatory.isEmpty()) {
            throw new IllegalStateException("Default CORE AGENT consent must contain an auto-approve scope");
        }

        List<String> optional = candidates.stream().filter(scope -> !mandatory.contains(scope)).toList();
        List<String> existing = existingConsentScopes == null
                ? List.of() : canonicalDistinct(existingConsentScopes, "existing consent scopes");
        List<String> preselected = existing.stream().filter(optional::contains).toList();
        return new CoreAgentConsentScopeOptions(candidates, mandatory, optional, preselected);
    }

    /**
     * Recomputes the options and returns scopes for this one explicit consent submission.
     *
     * <p>The method intentionally uses exact scope membership, not wildcard containment, for
     * submitted optional scopes. Pattern subtraction/minimization is not performed, so a broad
     * optional wildcard can coexist with a narrower mandatory pattern.</p>
     */
    public List<String> confirm(EffectiveRolePermissions effectiveRolePermissions,
                                CoreAgentRegisteredClientPolicy policy,
                                Collection<String> requestedScopes,
                                Collection<String> existingConsentScopes,
                                Collection<String> submittedOptionalScopes) {
        CoreAgentConsentScopeOptions options = options(effectiveRolePermissions, policy, requestedScopes,
                existingConsentScopes);
        List<String> submitted = canonicalDistinct(submittedOptionalScopes, "submitted optional scopes");
        if (!options.optionalScopes().containsAll(submitted)) {
            throw new IllegalArgumentException("Submitted consent scope is not optional for this request");
        }
        Set<String> issued = new LinkedHashSet<>(options.mandatoryScopes());
        issued.addAll(submitted);
        if (issued.isEmpty()) {
            throw new IllegalArgumentException("At least one CORE AGENT consent scope must be selected");
        }
        return issued.stream().sorted().toList();
    }

    private static void requireInputs(EffectiveRolePermissions effectiveRolePermissions,
                                      CoreAgentRegisteredClientPolicy policy) {
        if (effectiveRolePermissions == null) {
            throw new IllegalArgumentException("Effective role permissions are required");
        }
        if (policy == null) {
            throw new IllegalArgumentException("CORE AGENT registered client policy is required");
        }
    }

    private static List<String> canonicalDistinct(Collection<String> scopes, String source) {
        if (scopes == null) {
            throw new IllegalArgumentException(source + " cannot be null");
        }
        Set<String> canonicalScopes = new LinkedHashSet<>();
        for (String scope : scopes) {
            String canonicalScope = PermissionScopePattern.parse(scope).asScope();
            if (!canonicalScopes.add(canonicalScope)) {
                throw new IllegalArgumentException(source + " contains a duplicate scope pattern");
            }
        }
        return canonicalScopes.stream().sorted().toList();
    }

    private static List<String> withoutSuper(Collection<String> scopes) {
        return scopes.stream()
                .map(PermissionScopePattern::parse)
                .filter(pattern -> !pattern.isSuper())
                .map(PermissionScopePattern::asScope)
                .sorted()
                .toList();
    }

    private static List<String> meetWithoutSuper(Collection<String> candidates,
                                                  Collection<String> autoApproveScopes) {
        Set<String> mandatory = new LinkedHashSet<>();
        for (String candidate : candidates) {
            PermissionScopePattern candidatePattern = PermissionScopePattern.parse(candidate);
            for (String autoApprove : autoApproveScopes) {
                candidatePattern.meet(PermissionScopePattern.parse(autoApprove))
                        .filter(pattern -> !pattern.isSuper())
                        .map(PermissionScopePattern::asScope)
                        .ifPresent(mandatory::add);
            }
        }
        return mandatory.stream().sorted().toList();
    }
}
