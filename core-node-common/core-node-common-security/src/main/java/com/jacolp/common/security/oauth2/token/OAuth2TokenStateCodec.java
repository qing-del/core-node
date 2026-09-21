package com.jacolp.common.security.oauth2.token;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict schema-v1 Redis Hash representation for OAuth2 token state. */
public final class OAuth2TokenStateCodec {
    private static final String VERSION = "1";
    private static final List<String> REFRESH_FIELD_NAMES = List.of(
            "schema_version", "fingerprint", "verifier_hash", "user_id", "client_id", "granted_scopes",
            "issued_at_epoch_millis", "expires_at_epoch_millis");
    private static final List<String> SESSION_FIELD_NAMES = List.of(
            "schema_version", "user_id", "client_id", "current_access_jti", "access_expires_at_epoch_millis",
            "current_refresh_fingerprint", "refresh_expires_at_epoch_millis");
    private static final Set<String> REFRESH_FIELDS = Set.copyOf(REFRESH_FIELD_NAMES);
    private static final Set<String> SESSION_FIELDS = Set.copyOf(SESSION_FIELD_NAMES);

    /** Canonical fixed hash-field order for Redis Lua arguments. */
    public List<String> refreshFieldNames() { return REFRESH_FIELD_NAMES; }

    /** Canonical fixed hash-field order for Redis Lua arguments. */
    public List<String> sessionFieldNames() { return SESSION_FIELD_NAMES; }

    public Map<String, String> encode(RefreshTokenState state) {
        List<String> scopes = state.grantedScopes();
        if (scopes.stream().anyMatch(this::hasWhitespace)) {
            throw new IllegalArgumentException("scope must not contain whitespace");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("schema_version", VERSION); values.put("fingerprint", state.fingerprint()); values.put("verifier_hash", state.verifierHash());
        values.put("user_id", Long.toString(state.userId())); values.put("client_id", state.clientId()); values.put("granted_scopes", String.join(" ", scopes));
        values.put("issued_at_epoch_millis", Long.toString(state.issuedAt().toEpochMilli())); values.put("expires_at_epoch_millis", Long.toString(state.expiresAt().toEpochMilli()));
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> encode(OAuth2SessionState state) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("schema_version", VERSION); values.put("user_id", Long.toString(state.userId())); values.put("client_id", state.clientId());
        values.put("current_access_jti", state.currentAccessJti()); values.put("access_expires_at_epoch_millis", Long.toString(state.accessExpiresAt().toEpochMilli()));
        values.put("current_refresh_fingerprint", state.currentRefreshFingerprint()); values.put("refresh_expires_at_epoch_millis", Long.toString(state.refreshExpiresAt().toEpochMilli()));
        return Collections.unmodifiableMap(values);
    }

    public RefreshTokenState decodeRefresh(Map<String, String> values) {
        validateKeys(values, REFRESH_FIELDS);
        List<String> scopes = decodeScopes(values.get("granted_scopes"));
        return new RefreshTokenState(values.get("fingerprint"), values.get("verifier_hash"), positiveLong(values.get("user_id")), values.get("client_id"), scopes,
                instant(values.get("issued_at_epoch_millis")), instant(values.get("expires_at_epoch_millis")));
    }

    public OAuth2SessionState decodeSession(Map<String, String> values) {
        validateKeys(values, SESSION_FIELDS);
        return new OAuth2SessionState(positiveLong(values.get("user_id")), values.get("client_id"), values.get("current_access_jti"),
                instant(values.get("access_expires_at_epoch_millis")), values.get("current_refresh_fingerprint"), instant(values.get("refresh_expires_at_epoch_millis")));
    }

    private static void validateKeys(Map<String, String> values, Set<String> expected) {
        if (values == null || !values.keySet().equals(expected) || !VERSION.equals(values.get("schema_version")) || values.values().stream().anyMatch(value -> value == null)) throw new IllegalArgumentException("invalid OAuth2 token state schema");
    }
    private static long positiveLong(String value) { try { long parsed = Long.parseLong(value); if (parsed <= 0) throw new NumberFormatException(); return parsed; } catch (NumberFormatException e) { throw new IllegalArgumentException("invalid positive number"); } }
    private static Instant instant(String value) { try { return Instant.ofEpochMilli(Long.parseLong(value)); } catch (RuntimeException e) { throw new IllegalArgumentException("invalid epoch millis"); } }
    private List<String> decodeScopes(String value) { if (value.isEmpty()) return List.of(); List<String> scopes = List.of(value.split(" ", -1)); if (scopes.stream().anyMatch(scope -> scope.isBlank() || hasWhitespace(scope))) throw new IllegalArgumentException("invalid scope encoding"); return scopes; }
    private boolean hasWhitespace(String value) { return value.chars().anyMatch(Character::isWhitespace); }
}
