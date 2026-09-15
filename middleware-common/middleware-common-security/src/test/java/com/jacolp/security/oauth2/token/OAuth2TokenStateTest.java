package com.jacolp.security.oauth2.token;

import com.jacolp.common.security.oauth2.token.OAuth2SessionState;
import com.jacolp.common.security.oauth2.token.RefreshTokenState;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuth2TokenStateTest {
    private static final String FINGERPRINT = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";
    private static final String JTI = "AAECAwQFBgcICQoLDA0ODw";
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @Test void refreshStateCopiesAndSortsScopesWithoutExpandingWildcard() {
        List<String> scopes = new ArrayList<>(List.of("note:read", "*:read", "note:read"));
        RefreshTokenState state = new RefreshTokenState(FINGERPRINT, BCrypt.hashpw("x", BCrypt.gensalt()), 1, "core_agent", scopes, NOW, NOW.plusSeconds(60));
        scopes.add("late:scope");
        assertThat(state.grantedScopes()).containsExactly("*:read", "note:read");
        assertThatThrownBy(() -> state.grantedScopes().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(state.toString()).doesNotContain(state.verifierHash());
    }

    @Test void validatesStateFormatsTimesAndSafeClientId() {
        String hash = BCrypt.hashpw("x", BCrypt.gensalt());
        assertThatIllegalArgumentException().isThrownBy(() -> new RefreshTokenState(FINGERPRINT, hash, 1, "bad:client", List.of(), NOW, NOW.plusSeconds(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new RefreshTokenState(FINGERPRINT, hash, 1, "user_client", List.of(), NOW, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> new OAuth2SessionState(1, "admin_client", "bad", NOW, FINGERPRINT, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> new OAuth2SessionState(1, "x\n", JTI, NOW, FINGERPRINT, NOW));
    }

    @Test void stateContractsNeverContainRawTokenFields() {
        assertThat(RefreshTokenState.class.getRecordComponents()).extracting(RecordComponent::getName)
                .noneMatch(name -> name.toLowerCase().contains("raw") || name.equals("username") || name.equals("role"));
        assertThat(OAuth2SessionState.class.getRecordComponents()).extracting(RecordComponent::getName)
                .noneMatch(name -> name.toLowerCase().contains("raw") || name.equals("password") || name.equals("secret") || name.equals("claims"));
    }
}
