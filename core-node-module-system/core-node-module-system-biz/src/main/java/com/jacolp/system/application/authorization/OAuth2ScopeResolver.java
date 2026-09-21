package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.system.application.authorization.model.PermissionScopePattern;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes the finite intersection of role, client, request, and auto-approve scope patterns.
 */
@Component
public final class OAuth2ScopeResolver {

    private static final String CREATOR_ROLE_CODE = "CREATOR";

    public List<String> resolve(EffectiveRolePermissions effectiveRolePermissions,
                                Collection<String> clientScopes,
                                Collection<String> autoApproveScopes,
                                Collection<String> requestedScopes) {
        if (effectiveRolePermissions == null || !hasText(effectiveRolePermissions.roleCode())) {
            throw new IllegalArgumentException("Effective role permissions with a role code are required");
        }

        Set<PermissionScopePattern> rolePatterns = parseDistinct(effectiveRolePermissions.permissionCodes(),
                "role permission scopes");
        Set<PermissionScopePattern> clientPatterns = parseDistinct(clientScopes, "client scopes");
        Set<PermissionScopePattern> autoApprovePatterns = parseDistinct(autoApproveScopes, "auto-approve scopes");
        Set<PermissionScopePattern> roleClientMeet = meet(rolePatterns, clientPatterns);
        Set<PermissionScopePattern> resolvedPatterns;
        if (requestedScopes == null) {
            resolvedPatterns = withoutSuper(meet(roleClientMeet, autoApprovePatterns));
        } else {
            Set<PermissionScopePattern> requestPatterns = parseDistinct(requestedScopes, "requested scopes");
            resolvedPatterns = permitExplicitCreatorSuper(meet(roleClientMeet, requestPatterns),
                    effectiveRolePermissions.roleCode(), requestPatterns);
        }
        return resolvedPatterns.stream().map(PermissionScopePattern::asScope).sorted().toList();
    }

    /**
     * Narrows a previously granted scope set to an explicit refresh request without consulting a role or client
     * catalogue. The caller must invoke this only when the refresh request supplied {@code scope}; a missing
     * scope parameter has distinct protocol semantics and must not be represented as {@code null} here.
     *
     * <p>Both collections are strict, distinct permission patterns. The result is their pattern intersection,
     * so it can only preserve or narrow existing grants and cannot invent a wildcard or super permission.</p>
     */
    public List<String> narrowGrantedScopes(Collection<String> currentGrantedScopes,
                                            Collection<String> requestedScopes) {
        Set<PermissionScopePattern> currentPatterns = parseDistinct(currentGrantedScopes, "current granted scopes");
        Set<PermissionScopePattern> requestPatterns = parseDistinct(requestedScopes, "requested scopes");
        return meet(currentPatterns, requestPatterns).stream().map(PermissionScopePattern::asScope).sorted().toList();
    }

    private static Set<PermissionScopePattern> parseDistinct(Collection<String> scopes, String source) {
        if (scopes == null) {
            throw new IllegalArgumentException(source + " cannot be null");
        }
        Set<PermissionScopePattern> patterns = new LinkedHashSet<>();
        for (String scope : scopes) {
            PermissionScopePattern pattern = PermissionScopePattern.parse(scope);
            if (!patterns.add(pattern)) {
                throw new IllegalArgumentException(source + " contains a duplicate scope pattern");
            }
        }
        return patterns;
    }

    private static Set<PermissionScopePattern> meet(Collection<PermissionScopePattern> left,
                                                     Collection<PermissionScopePattern> right) {
        Set<PermissionScopePattern> result = new LinkedHashSet<>();
        for (PermissionScopePattern leftPattern : left) {
            for (PermissionScopePattern rightPattern : right) {
                leftPattern.meet(rightPattern).ifPresent(result::add);
            }
        }
        return result;
    }

    private static Set<PermissionScopePattern> withoutSuper(Collection<PermissionScopePattern> patterns) {
        return patterns.stream().filter(pattern -> !pattern.isSuper())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Set<PermissionScopePattern> permitExplicitCreatorSuper(Collection<PermissionScopePattern> patterns,
                                                                            String roleCode,
                                                                            Collection<PermissionScopePattern> requests) {
        return patterns.stream().filter(pattern -> !pattern.isSuper()
                        || (CREATOR_ROLE_CODE.equals(roleCode) && hasExplicitSuperRequestFor(pattern, requests)))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static boolean hasExplicitSuperRequestFor(PermissionScopePattern resolved,
                                                      Collection<PermissionScopePattern> requests) {
        return requests.stream().filter(PermissionScopePattern::isSuper)
                .anyMatch(request -> request.meet(resolved).isPresent());
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
