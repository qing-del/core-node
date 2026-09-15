package com.jacolp.system.application.authorization.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Immutable scope choices rendered by the CORE AGENT consent page.
 *
 * <p>Membership between the four collections is exact-pattern membership. In particular, this
 * object deliberately does not subtract or expand wildcard patterns: a mandatory
 * {@code note:read} and an optional {@code *:read} may both be present and may both be issued in
 * the resulting JWT.</p>
 */
public record CoreAgentConsentScopeOptions(
        List<String> candidateScopes,
        List<String> mandatoryScopes,
        List<String> optionalScopes,
        List<String> preselectedOptionalScopes) {

    public CoreAgentConsentScopeOptions {
        candidateScopes = sortedCopy(candidateScopes, "candidate scopes");
        mandatoryScopes = sortedCopy(mandatoryScopes, "mandatory scopes");
        optionalScopes = sortedCopy(optionalScopes, "optional scopes");
        preselectedOptionalScopes = sortedCopy(preselectedOptionalScopes, "preselected optional scopes");
        if (!optionalScopes.containsAll(preselectedOptionalScopes)) {
            throw new IllegalArgumentException("Preselected scopes must be optional scopes");
        }
    }

    private static List<String> sortedCopy(Collection<String> scopes, String source) {
        if (scopes == null) {
            throw new IllegalArgumentException(source + " cannot be null");
        }
        List<String> copy = new ArrayList<>(scopes.size());
        for (String scope : scopes) {
            copy.add(Objects.requireNonNull(scope, source + " cannot contain null"));
        }
        copy.sort(String::compareTo);
        for (int index = 1; index < copy.size(); index++) {
            if (copy.get(index - 1).equals(copy.get(index))) {
                throw new IllegalArgumentException(source + " cannot contain duplicates");
            }
        }
        return List.copyOf(copy);
    }
}
