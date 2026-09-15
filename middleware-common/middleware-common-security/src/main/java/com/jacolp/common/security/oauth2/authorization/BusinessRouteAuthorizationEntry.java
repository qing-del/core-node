package com.jacolp.common.security.oauth2.authorization;

import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** One exact method/path-pattern rule; every required scope must be granted. */
public record BusinessRouteAuthorizationEntry(HttpMethod method, String pathPattern,
                                              Set<String> requiredScopes, String requiredClientId) {

    private static final PathPatternParser PATH_PATTERN_PARSER = new PathPatternParser();

    public BusinessRouteAuthorizationEntry {
        method = Objects.requireNonNull(method, "method must not be null");
        if (!StringUtils.hasText(pathPattern) || !pathPattern.startsWith("/")) {
            throw new IllegalArgumentException("pathPattern must be an absolute Spring path pattern");
        }
        compiledPattern(pathPattern);
        requiredScopes = immutableScopes(requiredScopes);
        if (!"user".equals(requiredClientId) && !"admin".equals(requiredClientId)) {
            throw new IllegalArgumentException("requiredClientId must be user or admin");
        }
    }

    PathPattern compiledPattern() {
        return compiledPattern(pathPattern);
    }

    private static PathPattern compiledPattern(String pathPattern) {
        try {
            return PATH_PATTERN_PARSER.parse(pathPattern);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("pathPattern must be a valid Spring path pattern", exception);
        }
    }

    private static Set<String> immutableScopes(Set<String> scopes) {
        Objects.requireNonNull(scopes, "requiredScopes must not be null");
        if (scopes.isEmpty()) throw new IllegalArgumentException("requiredScopes must not be empty");
        LinkedHashSet<String> canonical = new LinkedHashSet<>();
        for (String scope : scopes) {
            PermissionScopeMatcher.grants(Set.of(scope), scope);
            if (!canonical.add(scope)) throw new IllegalArgumentException("requiredScopes must not contain duplicates");
        }
        return Set.copyOf(canonical);
    }
}
