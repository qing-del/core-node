package com.jacolp.system.application.authorization.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Immutable decision for one CORE AGENT browser authorization request. */
public record CoreAgentAuthorizationConsentDecision(
        CoreAgentConsentScopeOptions options,
        boolean consentRequired,
        List<String> reusedFinalScopes) {

    public CoreAgentAuthorizationConsentDecision {
        options = Objects.requireNonNull(options, "options");
        reusedFinalScopes = sortedCopy(reusedFinalScopes);
        if (consentRequired && !reusedFinalScopes.isEmpty()) {
            throw new IllegalArgumentException("Required consent cannot have reused final scopes");
        }
        if (!consentRequired && reusedFinalScopes.isEmpty()) {
            throw new IllegalArgumentException("Reused consent must have final scopes");
        }
    }

    @Override
    public String toString() {
        return "CoreAgentAuthorizationConsentDecision[options=<redacted>, consentRequired=" + consentRequired
                + ", reusedFinalScopes=<redacted>]";
    }

    private static List<String> sortedCopy(Collection<String> scopes) {
        if (scopes == null) {
            throw new IllegalArgumentException("reusedFinalScopes cannot be null");
        }
        List<String> copy = new ArrayList<>(scopes.size());
        for (String scope : scopes) {
            copy.add(Objects.requireNonNull(scope, "reusedFinalScopes cannot contain null"));
        }
        copy.sort(String::compareTo);
        for (int index = 1; index < copy.size(); index++) {
            if (copy.get(index - 1).equals(copy.get(index))) {
                throw new IllegalArgumentException("reusedFinalScopes cannot contain duplicates");
            }
        }
        return List.copyOf(copy);
    }
}
