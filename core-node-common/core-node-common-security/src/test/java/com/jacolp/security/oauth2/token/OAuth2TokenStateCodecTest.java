package com.jacolp.security.oauth2.token;

import com.jacolp.common.security.oauth2.token.OAuth2SessionState;
import com.jacolp.common.security.oauth2.token.OAuth2TokenStateCodec;
import com.jacolp.common.security.oauth2.token.RefreshTokenState;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCrypt;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OAuth2TokenStateCodecTest {
    private static final String FP = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";
    private static final String JTI = "AAECAwQFBgcICQoLDA0ODw";
    private final OAuth2TokenStateCodec codec = new OAuth2TokenStateCodec();
    private final Instant now = Instant.parse("2026-08-10T00:00:00Z");
    @Test void roundTripsRefreshAndSessionIncludingEmptyScopes() {
        RefreshTokenState refresh = new RefreshTokenState(FP, BCrypt.hashpw("x", BCrypt.gensalt()), 1, "core_agent", List.of("*:read", "note:read"), now, now.plusSeconds(60));
        assertThat(codec.decodeRefresh(codec.encode(refresh))).isEqualTo(refresh);
        assertThat(codec.decodeRefresh(codec.encode(new RefreshTokenState(FP, refresh.verifierHash(), 1, "user_client", List.of(), now, now.plusSeconds(1)))).grantedScopes()).isEmpty();
        OAuth2SessionState session = new OAuth2SessionState(1, "user_client", JTI, now.plusSeconds(1), FP, now.plusSeconds(2));
        assertThat(codec.decodeSession(codec.encode(session))).isEqualTo(session);
    }
    @Test void rejectsBadSchemaAndValues() {
        Map<String,String> missing = stateMap(); missing.remove("client_id"); assertThatIllegalArgumentException().isThrownBy(() -> codec.decodeRefresh(missing));
        Map<String,String> unknown = stateMap(); unknown.put("extra", "x"); assertThatIllegalArgumentException().isThrownBy(() -> codec.decodeRefresh(unknown));
        Map<String,String> version = stateMap(); version.put("schema_version", "2"); assertThatIllegalArgumentException().isThrownBy(() -> codec.decodeRefresh(version));
        Map<String,String> number = stateMap(); number.put("user_id", "bad"); assertThatIllegalArgumentException().isThrownBy(() -> codec.decodeRefresh(number));
    }
    @Test void rejectsBadTimeAndAmbiguousScopeEncoding() {
        assertThatIllegalArgumentException().isThrownBy(() -> codec.encode(new RefreshTokenState(FP, BCrypt.hashpw("x", BCrypt.gensalt()), 1, "core_agent", List.of("bad scope"), now, now.plusSeconds(1))));
        Map<String,String> values = stateMap(); values.put("issued_at_epoch_millis", "nope"); assertThatIllegalArgumentException().isThrownBy(() -> codec.decodeRefresh(values));
        assertThat(codec.encode(new OAuth2SessionState(1,"user_client",JTI,now,FP,now)).keySet()).noneMatch(key -> key.contains("token") || key.contains("password") || key.contains("secret") || key.contains("claims"));
    }
    private Map<String,String> stateMap() { return new HashMap<>(codec.encode(new RefreshTokenState(FP, BCrypt.hashpw("x", BCrypt.gensalt()), 1, "core_agent", List.of(), now, now.plusSeconds(1)))); }
}
