package com.jacolp.system.infrastructure.authorization;

import com.jacolp.system.application.authorization.model.PermissionScopePattern;
import com.jacolp.system.application.port.out.CoreAgentAuthorizationConsentStore;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Bridges the application consent port to Spring Authorization Server's official consent service. */
@Component
public final class SasCoreAgentAuthorizationConsentStore implements CoreAgentAuthorizationConsentStore {

    private static final String SCOPE_AUTHORITY_PREFIX = "SCOPE_";

    private final OAuth2AuthorizationConsentService authorizationConsentService;

    public SasCoreAgentAuthorizationConsentStore(OAuth2AuthorizationConsentService authorizationConsentService) {
        this.authorizationConsentService = Objects.requireNonNull(authorizationConsentService,
                "authorizationConsentService");
    }

    @Override
    public Optional<Set<String>> findScopes(String registeredClientId, String principalName) {
        validateIdentity(registeredClientId, principalName);
        OAuth2AuthorizationConsent consent = authorizationConsentService.findById(registeredClientId, principalName);
        if (consent == null) {
            return Optional.empty();
        }
        if (!registeredClientId.equals(consent.getRegisteredClientId()) || !principalName.equals(consent.getPrincipalName())) {
            throw new IllegalStateException("SAS authorization consent identity is inconsistent");
        }
        return Optional.of(sortedScopesFromAuthorities(consent.getAuthorities()));
    }

    @Override
    public void saveScopes(String registeredClientId, String principalName, Collection<String> scopes) {
        validateIdentity(registeredClientId, principalName);
        List<String> normalizedScopes = normalizedScopes(scopes);
        OAuth2AuthorizationConsent.Builder builder = OAuth2AuthorizationConsent.withId(registeredClientId, principalName);
        for (String scope : normalizedScopes) {
            builder.authority(new SimpleGrantedAuthority(SCOPE_AUTHORITY_PREFIX + scope));
        }
        authorizationConsentService.save(builder.build());
    }

    private static Set<String> sortedScopesFromAuthorities(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            throw new IllegalStateException("SAS authorization consent authorities are missing");
        }
        List<String> scopes = new ArrayList<>(authorities.size());
        Set<String> distinct = new LinkedHashSet<>();
        for (GrantedAuthority authority : authorities) {
            if (authority == null || authority.getAuthority() == null
                    || !authority.getAuthority().startsWith(SCOPE_AUTHORITY_PREFIX)) {
                throw new IllegalStateException("SAS authorization consent authority is invalid");
            }
            String scope = canonicalScope(authority.getAuthority().substring(SCOPE_AUTHORITY_PREFIX.length()),
                    "SAS authorization consent authority");
            if (!distinct.add(scope)) {
                throw new IllegalStateException("SAS authorization consent contains duplicate scopes");
            }
            scopes.add(scope);
        }
        scopes.sort(String::compareTo);
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(scopes));
    }

    private static List<String> normalizedScopes(Collection<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            throw new IllegalArgumentException("CORE AGENT consent scopes cannot be empty");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String scope : scopes) {
            String canonical = canonicalScope(scope, "CORE AGENT consent scope");
            if (!normalized.add(canonical)) {
                throw new IllegalArgumentException("CORE AGENT consent scopes cannot contain duplicates");
            }
        }
        return normalized.stream().sorted().toList();
    }

    private static String canonicalScope(String scope, String source) {
        if (scope == null || scope.isBlank() || !scope.equals(scope.trim())) {
            throw new IllegalArgumentException(source + " is invalid");
        }
        try {
            String canonical = PermissionScopePattern.parse(scope).asScope();
            if (!scope.equals(canonical)) {
                throw new IllegalArgumentException(source + " is non-canonical");
            }
            return canonical;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(source + " is invalid", exception);
        }
    }

    private static void validateIdentity(String registeredClientId, String principalName) {
        if (!isSafeText(registeredClientId)) {
            throw new IllegalArgumentException("CORE AGENT consent registered client id is invalid");
        }
        if (principalName == null || !principalName.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("CORE AGENT consent principal name is invalid");
        }
        try {
            if (Long.parseLong(principalName) <= 0) {
                throw new IllegalArgumentException("CORE AGENT consent principal name is invalid");
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("CORE AGENT consent principal name is invalid", exception);
        }
    }

    private static boolean isSafeText(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }
}
