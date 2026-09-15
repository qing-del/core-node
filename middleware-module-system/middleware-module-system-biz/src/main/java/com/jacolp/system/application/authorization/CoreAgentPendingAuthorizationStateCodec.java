package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.CoreAgentPendingAuthorizationState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict fixed-order version-one Redis Hash codec for pending CORE AGENT authorization state. */
public final class CoreAgentPendingAuthorizationStateCodec {

    private static final List<String> FIELDS = List.of(
            "schema_version", "client_id", "redirect_uri", "requested_scopes_present", "requested_scopes",
            "code_challenge", "code_challenge_method", "oauth_state", "original_socket_address", "user_id",
            "session_id", "issued_at_epoch_millis", "expires_at_epoch_millis");
    private static final Set<String> FIELD_SET = Set.copyOf(FIELDS);

    public List<String> fieldNames() {
        return FIELDS;
    }

    public Map<String, String> encode(CoreAgentPendingAuthorizationState state) {
        if (state == null) {
            throw invalid();
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("schema_version", "1");
        values.put("client_id", state.clientId());
        values.put("redirect_uri", state.redirectUri());
        values.put("requested_scopes_present", state.requestedScopes() == null ? "0" : "1");
        values.put("requested_scopes", state.requestedScopes() == null ? "" : String.join(",", state.requestedScopes()));
        values.put("code_challenge", state.codeChallenge());
        values.put("code_challenge_method", state.codeChallengeMethod());
        values.put("oauth_state", state.oauthState());
        values.put("original_socket_address", state.originalSocketAddress());
        values.put("user_id", Long.toString(state.authenticatedUserId()));
        values.put("session_id", state.sessionId());
        values.put("issued_at_epoch_millis", Long.toString(state.issuedAt().toEpochMilli()));
        values.put("expires_at_epoch_millis", Long.toString(state.expiresAt().toEpochMilli()));
        return Collections.unmodifiableMap(values);
    }

    public CoreAgentPendingAuthorizationState decode(Map<String, String> values) {
        if (values == null || !values.keySet().equals(FIELD_SET)
                || values.values().stream().anyMatch(Objects::isNull)) {
            throw invalid();
        }
        try {
            if (!"1".equals(values.get("schema_version"))
                    || !"0".equals(values.get("requested_scopes_present"))
                    && !"1".equals(values.get("requested_scopes_present"))) {
                throw invalid();
            }
            List<String> requestedScopes = "1".equals(values.get("requested_scopes_present"))
                    ? scopes(values.get("requested_scopes")) : null;
            if ((requestedScopes == null && !values.get("requested_scopes").isEmpty())
                    || (requestedScopes != null && values.get("requested_scopes").isEmpty())) {
                throw invalid();
            }
            return new CoreAgentPendingAuthorizationState(values.get("client_id"), values.get("redirect_uri"),
                    requestedScopes, values.get("code_challenge"), values.get("code_challenge_method"),
                    values.get("oauth_state"), values.get("original_socket_address"), positiveLong(values, "user_id"),
                    values.get("session_id"), Instant.ofEpochMilli(strictLong(values, "issued_at_epoch_millis")),
                    Instant.ofEpochMilli(strictLong(values, "expires_at_epoch_millis")));
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    @Override
    public String toString() {
        return "CoreAgentPendingAuthorizationStateCodec[schema=v1, fields=" + FIELDS + ']';
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

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid CORE AGENT pending authorization state map");
    }
}
