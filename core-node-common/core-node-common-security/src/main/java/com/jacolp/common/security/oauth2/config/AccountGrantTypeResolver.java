package com.jacolp.common.security.oauth2.config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure account-grant normalization and default-plus-extra resolution. */
public final class AccountGrantTypeResolver {

    public static final String PASSWORD = "password";
    public static final String EMAIL_CODE = "email-code";
    public static final String AUTHORIZATION_CODE = "authorization_code";
    public static final String REFRESH_TOKEN = "refresh_token";

    private static final List<String> REQUIRED_DEFAULT_GRANT_TYPES =
            List.of(PASSWORD, EMAIL_CODE, AUTHORIZATION_CODE);
    private static final Set<String> REQUIRED_DEFAULT_GRANT_TYPE_SET =
            Set.copyOf(REQUIRED_DEFAULT_GRANT_TYPES);
    private static final Pattern TOKEN = Pattern.compile("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+");

    private final Set<String> defaultGrantTypes;

    public AccountGrantTypeResolver(Collection<String> defaultGrantTypes) {
        this.defaultGrantTypes = normalizeDefaultGrantTypes(defaultGrantTypes);
    }

    public static List<String> requiredDefaultGrantTypes() {
        return REQUIRED_DEFAULT_GRANT_TYPES;
    }

    /**
     * Defaults are fixed for the first release. The property is configurable only as the
     * single source of that fixed set, not as an account-specific policy override.
     */
    public static Set<String> normalizeDefaultGrantTypes(Collection<String> defaultGrantTypes) {
        Set<String> normalized = normalizeTokens(defaultGrantTypes, "defaultGrantTypes");
        if (normalized.contains(REFRESH_TOKEN)) {
            throw new IllegalArgumentException("defaultGrantTypes must not contain refresh_token");
        }
        if (!normalized.equals(REQUIRED_DEFAULT_GRANT_TYPE_SET)) {
            throw new IllegalArgumentException("defaultGrantTypes must exactly contain password, email-code, authorization_code");
        }
        return immutableOrdered(normalized);
    }

    public Set<String> defaultGrantTypes() {
        return defaultGrantTypes;
    }

    /** Resolves CSV persisted in {@code sys_user.extra_grant_types}; blank means no additions. */
    public Set<String> effectiveGrantTypes(String extraGrantTypesCsv) {
        LinkedHashSet<String> effective = new LinkedHashSet<>(defaultGrantTypes);
        for (String extraGrantType : parseExtraGrantTypes(extraGrantTypesCsv)) {
            effective.add(extraGrantType);
        }
        return immutableOrdered(effective);
    }

    public boolean allows(String grantType, String extraGrantTypesCsv) {
        String normalizedGrantType = normalizeRequestedGrantType(grantType);
        return normalizedGrantType != null && effectiveGrantTypes(extraGrantTypesCsv).contains(normalizedGrantType);
    }

    private Set<String> parseExtraGrantTypes(String extraGrantTypesCsv) {
        if (extraGrantTypesCsv == null || extraGrantTypesCsv.trim().isEmpty()) {
            return Set.of();
        }
        Set<String> extras = normalizeTokens(List.of(extraGrantTypesCsv.split(",", -1)), "extraGrantTypes");
        if (extras.contains(REFRESH_TOKEN)) {
            throw new IllegalArgumentException("extraGrantTypes must not contain refresh_token");
        }
        for (String extra : extras) {
            if (defaultGrantTypes.contains(extra)) {
                throw new IllegalArgumentException("extraGrantTypes must not repeat default grant type: " + extra);
            }
        }
        return extras;
    }

    private static Set<String> normalizeTokens(Collection<String> values, String name) {
        Objects.requireNonNull(values, name + " must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                throw new IllegalArgumentException(name + " must not contain null");
            }
            String token = value.trim();
            if (token.isEmpty() || !TOKEN.matcher(token).matches()) {
                throw new IllegalArgumentException(name + " contains an invalid grant token");
            }
            if (!normalized.add(token)) {
                throw new IllegalArgumentException(name + " must not contain duplicate grant type: " + token);
            }
        }
        return normalized;
    }

    private static String normalizeRequestedGrantType(String grantType) {
        if (grantType == null) {
            return null;
        }
        String normalized = grantType.trim();
        return normalized.isEmpty() || !TOKEN.matcher(normalized).matches() ? null : normalized;
    }

    private static Set<String> immutableOrdered(Collection<String> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
