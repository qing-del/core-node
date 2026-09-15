package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationAccountSnapshot;
import com.jacolp.system.application.authorization.model.CoreAgentAuthorizationCodeState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Strict version-one, fixed-order Redis Hash codec for CORE AGENT authorization-code state.
 *
 * <p>The authorization code itself is deliberately absent from the hash and supplied only by the
 * lookup key. Nullable email uses the {@code email_present} discriminator, so a missing hash field
 * can never be confused with a null account email.</p>
 */
public final class CoreAgentAuthorizationCodeStateCodec {

    private static final List<String> FIELDS = List.of(
            "schema_version", "client_id", "redirect_uri", "scopes", "code_challenge", "code_challenge_method",
            "original_socket_address", "oauth_state", "issued_at_epoch_millis", "expires_at_epoch_millis",
            "user_id", "username", "role_id", "password_hash", "email_present", "email", "extra_grant_types",
            "status");
    private static final Set<String> FIELD_SET = Set.copyOf(FIELDS);

    public List<String> fieldNames() {
        return FIELDS;
    }

    public Map<String, String> encode(CoreAgentAuthorizationCodeState state) {
        if (state == null) {
            throw invalid();
        }
        CoreAgentAuthorizationAccountSnapshot account = state.accountSnapshot();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("schema_version", "1");
        values.put("client_id", state.clientId());
        values.put("redirect_uri", state.redirectUri());
        values.put("scopes", String.join(",", state.scopes()));
        values.put("code_challenge", state.codeChallenge());
        values.put("code_challenge_method", state.codeChallengeMethod());
        values.put("original_socket_address", state.originalSocketAddress());
        values.put("oauth_state", state.oauthState());
        values.put("issued_at_epoch_millis", Long.toString(state.issuedAt().toEpochMilli()));
        values.put("expires_at_epoch_millis", Long.toString(state.expiresAt().toEpochMilli()));
        values.put("user_id", Long.toString(account.userId()));
        values.put("username", account.username());
        values.put("role_id", Long.toString(account.roleId()));
        values.put("password_hash", account.passwordHash());
        values.put("email_present", account.email() == null ? "0" : "1");
        values.put("email", account.email() == null ? "" : account.email());
        values.put("extra_grant_types", account.extraGrantTypes());
        values.put("status", Integer.toString(account.status()));
        return Collections.unmodifiableMap(values);
    }

    public CoreAgentAuthorizationCodeState decode(String rawCode, Map<String, String> values) {
        if (values == null || !values.keySet().equals(FIELD_SET)
                || values.values().stream().anyMatch(Objects::isNull)) {
            throw invalid();
        }
        try {
            if (!"1".equals(values.get("schema_version"))
                    || !"0".equals(values.get("email_present")) && !"1".equals(values.get("email_present"))) {
                throw invalid();
            }
            String email = "1".equals(values.get("email_present")) ? values.get("email") : null;
            if ((email == null || email.isEmpty()) && "1".equals(values.get("email_present"))
                    || !values.get("email").isEmpty() && "0".equals(values.get("email_present"))) {
                throw invalid();
            }
            CoreAgentAuthorizationAccountSnapshot account = new CoreAgentAuthorizationAccountSnapshot(
                    positiveLong(values, "user_id"), values.get("username"), positiveLong(values, "role_id"),
                    values.get("password_hash"), email, values.get("extra_grant_types"), strictInt(values, "status"));
            return new CoreAgentAuthorizationCodeState(rawCode, values.get("client_id"), values.get("redirect_uri"),
                    scopes(values.get("scopes")), values.get("code_challenge"), values.get("code_challenge_method"),
                    values.get("original_socket_address"), values.get("oauth_state"),
                    Instant.ofEpochMilli(strictLong(values, "issued_at_epoch_millis")),
                    Instant.ofEpochMilli(strictLong(values, "expires_at_epoch_millis")), account);
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    @Override
    public String toString() {
        return "CoreAgentAuthorizationCodeStateCodec[schema=v1, fields=" + FIELDS + ']';
    }

    private static List<String> scopes(String csv) {
        if (csv == null || csv.isBlank()) {
            throw invalid();
        }
        String[] entries = csv.split(",", -1);
        List<String> scopes = new ArrayList<>(entries.length);
        for (String entry : entries) {
            if (entry.isEmpty()) {
                throw invalid();
            }
            scopes.add(entry);
        }
        return List.copyOf(scopes);
    }

    private static long strictLong(Map<String, String> values, String field) {
        String value = values.get(field);
        if (value == null || !value.matches("0|-[1-9][0-9]*|[1-9][0-9]*")) {
            throw invalid();
        }
        return Long.parseLong(value);
    }

    private static long positiveLong(Map<String, String> values, String field) {
        long value = strictLong(values, field);
        if (value <= 0) {
            throw invalid();
        }
        return value;
    }

    private static int strictInt(Map<String, String> values, String field) {
        String value = values.get(field);
        if (value == null || !value.matches("0|-[1-9][0-9]*|[1-9][0-9]*")) {
            throw invalid();
        }
        return Integer.parseInt(value);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid CORE AGENT authorization-code state map");
    }
}
