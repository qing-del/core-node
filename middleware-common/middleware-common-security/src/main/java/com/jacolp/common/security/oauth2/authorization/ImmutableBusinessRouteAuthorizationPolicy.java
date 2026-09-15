package com.jacolp.common.security.oauth2.authorization;

import com.jacolp.common.security.context.CurrentPrincipal;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Applies a validated, immutable route catalogue. A request matching more than one rule is denied
 * at construction time rather than relying on rule order.
 */
public final class ImmutableBusinessRouteAuthorizationPolicy implements BusinessRouteAuthorizationPolicy {

    private static final Set<String> USER_CLIENT_ROLES = Set.of("USER", "ADMIN", "CREATOR");

    private final List<BusinessRouteAuthorizationEntry> entries;

    public ImmutableBusinessRouteAuthorizationPolicy(Collection<BusinessRouteAuthorizationEntry> entries) {
        Objects.requireNonNull(entries, "entries must not be null");
        this.entries = List.copyOf(entries);
        if (this.entries.isEmpty()) throw new IllegalArgumentException("entries must not be empty");
        rejectDuplicateMethodPatterns(this.entries);
    }

    public List<BusinessRouteAuthorizationEntry> entries() {
        return entries;
    }

    @Override
    public Decision authorize(HttpMethod method, String requestPath, CurrentPrincipal principal) {
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(principal, "principal must not be null");
        PathContainer path = PathContainer.parsePath(requireAbsolutePath(requestPath));
        List<BusinessRouteAuthorizationEntry> matches = entries.stream()
                .filter(entry -> entry.method() == method && entry.compiledPattern().matches(path))
                .toList();
        if (matches.isEmpty()) return Decision.NO_MATCH;
        List<BusinessRouteAuthorizationEntry> orderedMatches = matches.stream()
                .sorted((left, right) -> org.springframework.web.util.pattern.PathPattern.SPECIFICITY_COMPARATOR
                        .compare(left.compiledPattern(), right.compiledPattern()))
                .toList();
        if (orderedMatches.size() > 1
                && org.springframework.web.util.pattern.PathPattern.SPECIFICITY_COMPARATOR.compare(
                orderedMatches.getFirst().compiledPattern(), orderedMatches.get(1).compiledPattern()) == 0) {
            throw new IllegalStateException("business route catalogue has ambiguous overlapping matches");
        }

        BusinessRouteAuthorizationEntry entry = orderedMatches.getFirst();
        if (!entry.requiredClientId().equals(principal.clientId())) return Decision.CLIENT_MISMATCH;
        if (!clientRoleMatches(entry.requiredClientId(), principal.roles())) return Decision.ROLE_MISMATCH;
        return PermissionScopeMatcher.grantsAll(principal.scopes(), entry.requiredScopes())
                ? Decision.ALLOW : Decision.SCOPE_MISMATCH;
    }

    private static void rejectDuplicateMethodPatterns(List<BusinessRouteAuthorizationEntry> entries) {
        List<String> duplicates = new ArrayList<>();
        for (int left = 0; left < entries.size(); left++) {
            for (int right = left + 1; right < entries.size(); right++) {
                BusinessRouteAuthorizationEntry first = entries.get(left);
                BusinessRouteAuthorizationEntry second = entries.get(right);
                if (first.method() == second.method() && first.pathPattern().equals(second.pathPattern())) {
                    duplicates.add(first.method() + " " + first.pathPattern());
                }
            }
        }
        if (!duplicates.isEmpty()) throw new IllegalArgumentException("duplicate business route entries: " + duplicates);
    }

    private static boolean clientRoleMatches(String clientId, List<String> roles) {
        return switch (clientId) {
            case "user" -> roles.size() == 1 && USER_CLIENT_ROLES.contains(roles.getFirst());
            case "admin" -> roles.equals(List.of("ADMIN")) || roles.equals(List.of("CREATOR"));
            default -> false;
        };
    }

    private static String requireAbsolutePath(String requestPath) {
        if (requestPath == null || !requestPath.startsWith("/")) {
            throw new IllegalArgumentException("requestPath must be absolute");
        }
        return requestPath;
    }
}
