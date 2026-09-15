package com.jacolp.system.application.authorization.model;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A strict, two-component OAuth scope pattern such as {@code note:read} or {@code *:read}.
 */
public record PermissionScopePattern(String resource, String action) {

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    public PermissionScopePattern {
        resource = normalizeComponent(resource, "resource");
        action = normalizeComponent(action, "action");
    }

    public static PermissionScopePattern parse(String scope) {
        if (scope == null) {
            throw new IllegalArgumentException("Scope cannot be null");
        }
        String normalizedScope = scope.trim();
        int separator = normalizedScope.indexOf(':');
        if (separator <= 0 || separator != normalizedScope.lastIndexOf(':')
                || separator == normalizedScope.length() - 1) {
            throw new IllegalArgumentException("Scope must contain exactly one resource:action separator");
        }
        return new PermissionScopePattern(normalizedScope.substring(0, separator),
                normalizedScope.substring(separator + 1));
    }

    public Optional<PermissionScopePattern> meet(PermissionScopePattern other) {
        if (other == null) {
            throw new IllegalArgumentException("Scope pattern cannot be null");
        }
        Optional<String> resourceMeet = meetComponent(resource, other.resource);
        Optional<String> actionMeet = meetComponent(action, other.action);
        return resourceMeet.isPresent() && actionMeet.isPresent()
                ? Optional.of(new PermissionScopePattern(resourceMeet.get(), actionMeet.get()))
                : Optional.empty();
    }

    public boolean isSuper() {
        return "super".equals(action);
    }

    public String asScope() {
        return resource + ':' + action;
    }

    private static String normalizeComponent(String component, String name) {
        if (component == null) {
            throw new IllegalArgumentException("Scope " + name + " cannot be null");
        }
        String normalizedComponent = component.trim();
        if (!"*".equals(normalizedComponent) && !TOKEN.matcher(normalizedComponent).matches()) {
            throw new IllegalArgumentException("Scope " + name + " must be a token or *");
        }
        return normalizedComponent;
    }

    private static Optional<String> meetComponent(String left, String right) {
        if (left.equals(right)) {
            return Optional.of(left);
        }
        if ("*".equals(left)) {
            return Optional.of(right);
        }
        if ("*".equals(right)) {
            return Optional.of(left);
        }
        return Optional.empty();
    }
}
