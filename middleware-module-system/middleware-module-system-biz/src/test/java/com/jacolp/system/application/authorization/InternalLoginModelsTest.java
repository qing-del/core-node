package com.jacolp.system.application.authorization;

import com.jacolp.system.application.authorization.model.InternalIssuedTokens;
import com.jacolp.system.application.authorization.model.InternalLoginRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class InternalLoginModelsTest {

    @Test
    void grantsAreMutuallyExclusiveAndScopeIntentIsPreserved() {
        InternalLoginRequest password = new InternalLoginRequest(
                "user", "password", "alice", "pw", null, null, null, "192.0.2.1");
        InternalLoginRequest code = new InternalLoginRequest(
                "admin", "email-code", null, null, "a@b.test", "012345", Set.of(), "192.0.2.1");

        assertThat(password.requestedScopes()).isNull();
        assertThat(code.requestedScopes()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> new InternalLoginRequest(
                "user", "password", "a", "p", "a@b", null, Set.of(), "ip"));
    }

    @Test
    void issuedTokensSortAndRedact() {
        Instant now = Instant.EPOCH;
        InternalIssuedTokens tokens = new InternalIssuedTokens(
                "jwt-one", "refresh-secret", "Bearer", now, now.plusSeconds(1), now.plusSeconds(2),
                List.of("z:read", "a:read"));

        assertThat(tokens.grantedScopes()).containsExactly("a:read", "z:read");
        assertThat(tokens.toString()).doesNotContain("jwt-one", "refresh-secret", "a:read");
        assertThatIllegalArgumentException().isThrownBy(() ->
                new InternalIssuedTokens("a", "r", "Bearer", now, now, now, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new InternalIssuedTokens(
                "a", "r", "Bearer", now, now.plusSeconds(1), now.plusSeconds(2), null));
        assertThat(new InternalIssuedTokens(
                "a", "r", "Bearer", now, now.plusSeconds(1), now.plusSeconds(2), List.of()).grantedScopes())
                .isEmpty();
    }
}
