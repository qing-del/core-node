package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.EmailLoginCodeState;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict version-one String Map codec for future Redis/Lua storage. */
public final class EmailLoginCodeStateCodec {
    private static final List<String> FIELDS = List.of("schema_version", "client_id", "user_id", "email_fingerprint",
            "verifier_hash", "failed_attempts", "issued_at_epoch_millis", "expires_at_epoch_millis");
    private static final Set<String> FIELD_SET = Set.copyOf(FIELDS);

    public List<String> fieldNames() {
        return FIELDS;
    }

    public Map<String, String> encode(EmailLoginCodeState state) {
        if (state == null) {
            throw invalid();
        }
        Map<String, String> map = new LinkedHashMap<>();
        map.put("schema_version", "1");
        map.put("client_id", state.clientId());
        map.put("user_id", state.userId().toString());
        map.put("email_fingerprint", state.emailFingerprint());
        map.put("verifier_hash", state.verifierHash());
        map.put("failed_attempts", state.failedAttempts().toString());
        map.put("issued_at_epoch_millis", Long.toString(state.issuedAt().toEpochMilli()));
        map.put("expires_at_epoch_millis", Long.toString(state.expiresAt().toEpochMilli()));
        return Collections.unmodifiableMap(map);
    }

    public EmailLoginCodeState decode(Map<String, String> map) {
        if (map == null || !map.keySet().equals(FIELD_SET)
                || map.values().stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid();
        }
        try {
            if (!"1".equals(map.get("schema_version"))) {
                throw invalid();
            }
            return new EmailLoginCodeState(map.get("client_id"), strictPositiveLong(map, "user_id"), map.get("email_fingerprint"),
                    map.get("verifier_hash"), strictInt(map, "failed_attempts"), Instant.ofEpochMilli(strictLong(map, "issued_at_epoch_millis")),
                    Instant.ofEpochMilli(strictLong(map, "expires_at_epoch_millis")));
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    private static long strictLong(Map<String, String> map, String key) {
        String value = map.get(key);
        if (!value.matches("0|-[1-9][0-9]*|[1-9][0-9]*")) {
            throw invalid();
        }
        return Long.parseLong(value);
    }
    private static int strictInt(Map<String, String> map, String key) {
        String value = map.get(key);
        if (!value.matches("0|-[1-9][0-9]*|[1-9][0-9]*")) {
            throw invalid();
        }
        return Integer.parseInt(value);
    }
    private static long strictPositiveLong(Map<String, String> map, String key) {
        long value = strictLong(map, key);
        if (value <= 0) {
            throw invalid();
        }
        return value;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid email-code state map");
    }
}
